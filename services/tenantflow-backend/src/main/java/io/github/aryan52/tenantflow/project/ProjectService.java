package io.github.aryan52.tenantflow.project;

import io.github.aryan52.tenantflow.entity.Project;
import io.github.aryan52.tenantflow.entity.ProjectStatus;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import io.github.aryan52.tenantflow.entity.Tenant;
import io.github.aryan52.tenantflow.error.ResourceNotFoundException;
import io.github.aryan52.tenantflow.project.dto.CreateProjectRequest;
import io.github.aryan52.tenantflow.project.dto.ProjectResponse;
import io.github.aryan52.tenantflow.project.dto.UpdateProjectRequest;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import io.github.aryan52.tenantflow.repository.TaskRepository;
import io.github.aryan52.tenantflow.repository.TaskRepository.ProjectTaskCounts;
import io.github.aryan52.tenantflow.repository.TenantRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import java.util.List;
import java.util.Map;
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
		Tenant tenant = tenantRepository.findById(tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Tenant"));

		Project project = Project.builder()
				.tenant(tenant)
				.name(request.name().trim())
				.description(trimToNull(request.description()))
				.status(request.status() != null ? request.status() : ProjectStatus.ACTIVE)
				.createdBy(userRepository.getReferenceById(currentUserId))
				.build();

		return toResponse(projectRepository.save(project));
	}

	@Transactional
	public ProjectResponse updateProject(UUID tenantId, UUID projectId, UpdateProjectRequest request) {
		Project project = findProject(tenantId, projectId);

		project.setName(request.name().trim());
		project.setDescription(trimToNull(request.description()));
		if (request.status() != null) {
			project.setStatus(request.status());
		}

		return toResponse(projectRepository.save(project));
	}

	@Transactional
	public void deleteProject(UUID tenantId, UUID projectId) {
		Project project = findProject(tenantId, projectId);
		// Explicit rather than relying on the ON DELETE CASCADE alone, so the behaviour
		// does not depend on the database schema the entities are running against.
		taskRepository.deleteAllByProjectId(project.getId());
		projectRepository.delete(project);
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

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
