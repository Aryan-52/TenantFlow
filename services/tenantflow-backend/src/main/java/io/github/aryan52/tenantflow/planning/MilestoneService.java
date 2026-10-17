package io.github.aryan52.tenantflow.planning;

import io.github.aryan52.tenantflow.activity.ActivityService;
import io.github.aryan52.tenantflow.automation.AutomationService;
import io.github.aryan52.tenantflow.entity.Project;
import io.github.aryan52.tenantflow.error.ConflictException;
import io.github.aryan52.tenantflow.error.ResourceNotFoundException;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MilestoneService {

	public record MilestoneRequest(
			@NotBlank @Size(max = 160) String name,
			@Size(max = 2000) String description,
			@NotNull LocalDate targetDate,
			Milestone.Status status,
			Long version) {
	}

	public record MilestoneResponse(UUID id, UUID projectId, String projectName, String name, String description,
			LocalDate targetDate, Milestone.Status status, Instant completedAt, long totalTasks, long doneTasks,
			long version) {
	}

	private final MilestoneRepository milestoneRepository;
	private final ProjectRepository projectRepository;
	private final ActivityService activityService;
	private final AutomationService automationService;

	@Transactional(readOnly = true)
	public List<MilestoneResponse> list(UUID tenantId, UUID projectId) {
		Project project = findProject(tenantId, projectId);
		return toResponses(milestoneRepository.findByProjectIdOrderByTargetDateAscNameAsc(projectId), Map.of(projectId, project.getName()));
	}

	@Transactional
	public MilestoneResponse create(UUID tenantId, UUID projectId, UUID userId, MilestoneRequest request) {
		Project project = findProject(tenantId, projectId);
		Milestone m = milestoneRepository.saveAndFlush(Milestone.builder()
				.tenantId(tenantId)
				.projectId(projectId)
				.name(request.name().trim())
				.description(trimToNull(request.description()))
				.targetDate(request.targetDate())
				.status(request.status() == null ? Milestone.Status.OPEN : request.status())
				.createdBy(userId)
				.build());
		activityService.record(tenantId, projectId, null, "MILESTONE_CREATED",
				"added milestone \"" + m.getName() + "\" (" + m.getTargetDate() + ") to " + project.getName(), List.of());
		if (m.getStatus() == Milestone.Status.DONE) {
			completed(tenantId, project, m);
		}
		return toResponses(List.of(m), Map.of(projectId, project.getName())).get(0);
	}

	@Transactional
	public MilestoneResponse update(UUID tenantId, UUID projectId, UUID milestoneId, MilestoneRequest request) {
		Project project = findProject(tenantId, projectId);
		Milestone m = find(tenantId, projectId, milestoneId);
		if (request.version() != null && request.version() != m.getVersion()) {
			throw new ConflictException("This milestone was changed by someone else. Reload it and try again.");
		}
		Milestone.Status before = m.getStatus();
		LocalDate beforeDate = m.getTargetDate();
		m.setName(request.name().trim());
		m.setDescription(trimToNull(request.description()));
		m.setTargetDate(request.targetDate());
		if (request.status() != null) {
			m.setStatus(request.status());
		}
		Milestone saved = milestoneRepository.saveAndFlush(m);
		if (before != Milestone.Status.DONE && saved.getStatus() == Milestone.Status.DONE) {
			completed(tenantId, project, saved);
		} else if (before == Milestone.Status.DONE && saved.getStatus() == Milestone.Status.OPEN) {
			activityService.record(tenantId, projectId, null, "MILESTONE_REOPENED",
					"reopened milestone \"" + saved.getName() + "\"", List.of());
		}
		if (!beforeDate.equals(saved.getTargetDate())) {
			activityService.record(tenantId, projectId, null, "MILESTONE_RESCHEDULED",
					"moved milestone \"" + saved.getName() + "\" to " + saved.getTargetDate(), List.of());
		}
		return toResponses(List.of(saved), Map.of(projectId, project.getName())).get(0);
	}

	@Transactional
	public void delete(UUID tenantId, UUID projectId, UUID milestoneId) {
		Project project = findProject(tenantId, projectId);
		Milestone m = find(tenantId, projectId, milestoneId);
		// Linked tasks stay; they simply lose the milestone link.
		milestoneRepository.detachTasks(m.getId());
		milestoneRepository.delete(m);
		activityService.record(tenantId, projectId, null, "MILESTONE_DELETED",
				"deleted milestone \"" + m.getName() + "\" from " + project.getName(), List.of());
	}

	/** Milestones of the whole workspace in a date range (planning calendar, dashboard). */
	@Transactional(readOnly = true)
	public List<MilestoneResponse> listForTenant(UUID tenantId, LocalDate from, LocalDate to) {
		List<Milestone> milestones = milestoneRepository.findByTenantIdAndTargetDateBetweenOrderByTargetDateAsc(tenantId, from, to);
		return toResponses(milestones, projectNames(tenantId));
	}

	@Transactional(readOnly = true)
	public List<MilestoneResponse> listAllForTenant(UUID tenantId) {
		return toResponses(milestoneRepository.findByTenantIdOrderByTargetDateAsc(tenantId), projectNames(tenantId));
	}

	/** Validates that a milestone belongs to the given project (for linking tasks). */
	@Transactional(readOnly = true)
	public void requireInProject(UUID tenantId, UUID projectId, UUID milestoneId) {
		find(tenantId, projectId, milestoneId);
	}

	private void completed(UUID tenantId, Project project, Milestone m) {
		activityService.record(tenantId, project.getId(), null, "MILESTONE_COMPLETED",
				"completed milestone \"" + m.getName() + "\" in " + project.getName(), List.of());
		automationService.onMilestoneCompleted(tenantId, project, m);
	}

	private Map<UUID, String> projectNames(UUID tenantId) {
		Map<UUID, String> names = new HashMap<>();
		projectRepository.findByTenant_Id(tenantId).forEach(p -> names.put(p.getId(), p.getName()));
		return names;
	}

	private List<MilestoneResponse> toResponses(List<Milestone> milestones, Map<UUID, String> projectNames) {
		Collection<UUID> ids = milestones.stream().map(Milestone::getId).toList();
		Map<UUID, long[]> counts = new HashMap<>();
		if (!ids.isEmpty()) {
			for (Object[] row : milestoneRepository.taskCounts(ids)) {
				counts.put((UUID) row[0], new long[] { ((Number) row[1]).longValue(), row[2] == null ? 0 : ((Number) row[2]).longValue() });
			}
		}
		return milestones.stream().map(m -> {
			long[] c = counts.getOrDefault(m.getId(), new long[] { 0, 0 });
			return new MilestoneResponse(m.getId(), m.getProjectId(), projectNames.get(m.getProjectId()), m.getName(),
					m.getDescription(), m.getTargetDate(), m.getStatus(), m.getCompletedAt(), c[0], c[1], m.getVersion());
		}).toList();
	}

	private Project findProject(UUID tenantId, UUID projectId) {
		return projectRepository.findByIdAndTenant_Id(projectId, tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Project"));
	}

	private Milestone find(UUID tenantId, UUID projectId, UUID milestoneId) {
		return milestoneRepository.findByIdAndProjectIdAndTenantId(milestoneId, projectId, tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Milestone"));
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String t = value.trim();
		return t.isEmpty() ? null : t;
	}
}
