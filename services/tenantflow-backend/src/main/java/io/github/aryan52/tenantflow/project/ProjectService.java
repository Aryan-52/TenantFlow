package io.github.aryan52.tenantflow.project;

import io.github.aryan52.tenantflow.activity.ActivityEventRepository;
import io.github.aryan52.tenantflow.activity.ActivityService;
import io.github.aryan52.tenantflow.activity.ActivityService.Notify;
import io.github.aryan52.tenantflow.audit.AuditAction;
import io.github.aryan52.tenantflow.audit.AuditService;
import io.github.aryan52.tenantflow.audit.AuditService.Entry;
import io.github.aryan52.tenantflow.cache.TenantCacheInvalidator;
import io.github.aryan52.tenantflow.attachment.AttachmentService;
import io.github.aryan52.tenantflow.entity.Project;
import io.github.aryan52.tenantflow.idempotency.IdempotencyService;
import io.github.aryan52.tenantflow.entity.ProjectStatus;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.entity.Tenant;
import io.github.aryan52.tenantflow.error.ConflictException;
import io.github.aryan52.tenantflow.error.ResourceNotFoundException;
import io.github.aryan52.tenantflow.project.dto.CreateProjectRequest;
import io.github.aryan52.tenantflow.project.dto.ProjectResponse;
import io.github.aryan52.tenantflow.project.dto.UpdateProjectRequest;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import io.github.aryan52.tenantflow.repository.TaskRepository;
import io.github.aryan52.tenantflow.repository.TaskRepository.ProjectTaskCounts;
import io.github.aryan52.tenantflow.repository.TenantRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectService {

	private final ProjectRepository projectRepository;
	private final TenantRepository tenantRepository;
	private final TaskRepository taskRepository;
	private final UserRepository userRepository;
	private final MembershipRepository membershipRepository;
	private final AuditService auditService;
	private final TenantCacheInvalidator cacheInvalidator;
	private final ActivityService activityService;
	private final ActivityEventRepository activityEventRepository;
	private final IdempotencyService idempotencyService;
	private final AttachmentService attachmentService;
	private final io.github.aryan52.tenantflow.planning.TaskDependencyRepository dependencyRepository;
	private final io.github.aryan52.tenantflow.planning.MilestoneRepository milestoneRepository;
	private final io.github.aryan52.tenantflow.task.TaskFocusRepository focusRepository;

	@Transactional(readOnly = true)
	public List<ProjectResponse> getProjects(UUID tenantId) {
		if (!tenantRepository.existsById(tenantId)) {
			throw new ResourceNotFoundException("Tenant");
		}
		Map<UUID, ProjectTaskCounts> counts = taskRepository.countByProjectForTenant(tenantId, TaskStatus.DONE).stream()
				.collect(Collectors.toMap(ProjectTaskCounts::getProjectId, Function.identity()));
		return projectRepository.findByTenant_Id(tenantId).stream()
				.map(project -> {
					ProjectTaskCounts c = counts.get(project.getId());
					return ProjectResponse.from(project, c != null ? c.getTotal() : 0, c != null ? c.getCompleted() : 0);
				})
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public ProjectResponse getProject(UUID tenantId, UUID projectId) {
		return toResponse(findProject(tenantId, projectId));
	}

	@Transactional
	public ProjectResponse createProject(UUID tenantId, UUID currentUserId, CreateProjectRequest request) {
		return createProject(tenantId, currentUserId, request, null);
	}

	/** With an Idempotency-Key, a replayed create returns the project the first request created. */
	@Transactional
	public ProjectResponse createProject(UUID tenantId, UUID currentUserId, CreateProjectRequest request,
			String idempotencyKey) {
		Tenant tenant = tenantRepository.findById(tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Tenant"));
		UUID projectId = idempotencyService.run(currentUserId, "project-create:" + tenantId, idempotencyKey, request,
				() -> doCreateProject(tenant, currentUserId, request));
		return toResponse(findProject(tenantId, projectId));
	}

	private UUID doCreateProject(Tenant tenant, UUID currentUserId, CreateProjectRequest request) {
		UUID tenantId = tenant.getId();

		User creator = userRepository.getReferenceById(currentUserId);
		// Omitted owner: the creator owns the project (they are a member - the controller
		// only lets workspace admins/owners create projects).
		User owner = request.isOwnerIdPresent() ? resolveOwner(tenantId, request.getOwnerId(), null) : creator;

		Project project = Project.builder()
				.tenant(tenant)
				.name(request.getName().trim())
				.description(trimToNull(request.getDescription()))
				.status(request.getStatus() != null ? request.getStatus() : ProjectStatus.ACTIVE)
				.createdBy(creator)
				.owner(owner)
				.startDate(request.getStartDate())
				.targetDate(request.getTargetDate())
				.build();
		validateDates(project);

		Project saved = projectRepository.saveAndFlush(project);
		activityService.record(tenantId, saved.getId(), null, "PROJECT_CREATED", "created project " + quote(saved),
				ownerNotice(tenantId, saved));
		cacheInvalidator.invalidate(tenantId);
		return saved.getId();
	}

	@Transactional
	public ProjectResponse updateProject(UUID tenantId, UUID projectId, UpdateProjectRequest request) {
		Project project = findProject(tenantId, projectId);
		if (request.getVersion() != null && request.getVersion() != project.getVersion()) {
			throw new ConflictException("This project was changed by someone else. Reload it and try again.");
		}
		String previousName = project.getName();
		ProjectStatus previousStatus = project.getStatus();
		UUID previousOwnerId = project.getOwner() == null ? null : project.getOwner().getId();
		String previousDescription = project.getDescription();

		project.setName(request.getName().trim());
		project.setDescription(trimToNull(request.getDescription()));
		if (request.getStatus() != null) {
			project.setStatus(request.getStatus());
		}
		// Omitted owner: keep the current one; an explicit null unassigns.
		if (request.isOwnerIdPresent()) {
			project.setOwner(resolveOwner(tenantId, request.getOwnerId(), project.getOwner()));
		}
		java.time.LocalDate previousTarget = project.getTargetDate();
		if (request.isStartDatePresent()) {
			project.setStartDate(request.getStartDate());
		}
		if (request.isTargetDatePresent()) {
			project.setTargetDate(request.getTargetDate());
		}
		validateDates(project);

		Project saved = projectRepository.saveAndFlush(project);
		recordProjectChanges(tenantId, saved, previousName, previousDescription, previousStatus, previousOwnerId);
		if (!Objects.equals(previousTarget, saved.getTargetDate())) {
			activityService.record(tenantId, saved.getId(), null, "PROJECT_TARGET_CHANGED", saved.getTargetDate() == null
					? "removed the target date of " + quote(saved)
					: "set the target date of " + quote(saved) + " to " + saved.getTargetDate(), List.of());
		}
		cacheInvalidator.invalidate(tenantId);
		return toResponse(saved);
	}

	@Transactional
	public void deleteProject(UUID tenantId, UUID projectId) {
		Project project = findProject(tenantId, projectId);
		// Explicit rather than relying on the ON DELETE CASCADE alone, so the behaviour
		// does not depend on the database schema the entities are running against.
		long taskCount = taskRepository.countByProject_Id(project.getId());
		// History stays, detached from the project; the deletion itself is workspace-level.
		activityEventRepository.detachProject(project.getId());
		activityService.record(tenantId, null, null, "PROJECT_DELETED",
				"deleted project " + quote(project) + (taskCount > 0 ? " and its " + taskCount + " task(s)" : ""), List.of());
		attachmentService.deleteAllForProject(project.getId());
		dependencyRepository.deleteAllForProject(project.getId());
		focusRepository.deleteAllForProject(project.getId());
		taskRepository.deleteAllByProjectId(project.getId());
		milestoneRepository.deleteAllByProjectId(project.getId());
		projectRepository.delete(project);
		cacheInvalidator.invalidate(tenantId);
		auditService.record(Entry.success(AuditAction.PROJECT_DELETED, tenantId, "PROJECT", projectId,
				"tasks deleted=" + taskCount));
	}

	private void recordProjectChanges(UUID tenantId, Project p, String previousName, String previousDescription,
			ProjectStatus previousStatus, UUID previousOwnerId) {
		if (!previousName.equals(p.getName())) {
			activityService.record(tenantId, p.getId(), null, "PROJECT_RENAMED",
					"renamed project \"" + previousName + "\" to " + quote(p), List.of());
		} else if (!java.util.Objects.equals(previousDescription, p.getDescription())) {
			activityService.record(tenantId, p.getId(), null, "PROJECT_EDITED", "edited project " + quote(p), List.of());
		}
		if (previousStatus != p.getStatus()) {
			activityService.record(tenantId, p.getId(), null, "PROJECT_STATUS_CHANGED",
					"changed the status of " + quote(p) + " to " + p.getStatus().name().replace('_', ' ').toLowerCase(), List.of());
		}
		UUID ownerId = p.getOwner() == null ? null : p.getOwner().getId();
		if (!java.util.Objects.equals(previousOwnerId, ownerId)) {
			activityService.record(tenantId, p.getId(), null, "PROJECT_OWNER_CHANGED",
					p.getOwner() == null ? "removed the owner of " + quote(p)
							: "made " + p.getOwner().getName() + " the owner of " + quote(p),
					ownerNotice(tenantId, p));
		}
	}

	/** Tells the (new) owner they now lead the project; the version makes each change unique. */
	private List<Notify> ownerNotice(UUID tenantId, Project p) {
		if (p.getOwner() == null) {
			return List.of();
		}
		return List.of(Notify.of(p.getOwner().getId(), "PROJECT_OWNER_ASSIGNED", "You now own project " + p.getName(),
				null, "/tenants/" + tenantId + "/projects/" + p.getId(),
				"project:" + p.getId() + ":v" + p.getVersion() + ":owner:" + p.getOwner().getId()));
	}

	private static String quote(Project p) {
		return "\"" + p.getName() + "\"";
	}

	private Project findProject(UUID tenantId, UUID projectId) {
		return projectRepository.findByIdAndTenant_Id(projectId, tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Project"));
	}

	private ProjectResponse toResponse(Project project) {
		return ProjectResponse.from(
				project,
				taskRepository.countByProject_Id(project.getId()),
				taskRepository.countByProject_IdAndStatus(project.getId(), TaskStatus.DONE)
		);
	}

	/**
	 * Resolves a requested owner: null = unassigned. Anyone else must be a member of this
	 * project's workspace - the id comes from the client, so it is never trusted on its own.
	 * Keeping the current owner unchanged is always allowed.
	 */
	private User resolveOwner(UUID tenantId, UUID id, User currentOwner) {
		if (id == null) {
			return null;
		}
		if (currentOwner != null && Objects.equals(currentOwner.getId(), id)) {
			return currentOwner;
		}
		if (!membershipRepository.existsByUser_IdAndTenant_Id(id, tenantId)) {
			throw new IllegalArgumentException("Project owner must be a member of this workspace");
		}
		return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User"));
	}

	private static void validateDates(Project p) {
		if (p.getStartDate() != null && p.getTargetDate() != null && p.getTargetDate().isBefore(p.getStartDate())) {
			throw new IllegalArgumentException("The target date must be on or after the start date");
		}
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
