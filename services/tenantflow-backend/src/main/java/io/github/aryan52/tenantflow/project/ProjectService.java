package io.github.aryan52.tenantflow.project;

import io.github.aryan52.tenantflow.entity.Project;
import io.github.aryan52.tenantflow.entity.Tenant;
import io.github.aryan52.tenantflow.error.ResourceNotFoundException;
import io.github.aryan52.tenantflow.project.dto.CreateProjectRequest;
import io.github.aryan52.tenantflow.project.dto.ProjectResponse;
import io.github.aryan52.tenantflow.project.dto.UpdateProjectRequest;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import io.github.aryan52.tenantflow.repository.TenantRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectService {

	private final ProjectRepository projectRepository;
	private final TenantRepository tenantRepository;

	@Transactional(readOnly = true)
	public List<ProjectResponse> getProjects(UUID tenantId) {
		if (!tenantRepository.existsById(tenantId)) {
			throw new ResourceNotFoundException("Tenant");
		}
		return projectRepository.findByTenant_Id(tenantId).stream()
				.map(ProjectResponse::from)
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public ProjectResponse getProject(UUID tenantId, UUID projectId) {
		Project project = projectRepository.findByIdAndTenant_Id(projectId, tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Project"));
		return ProjectResponse.from(project);
	}

	@Transactional
	public ProjectResponse createProject(UUID tenantId, CreateProjectRequest request) {
		Tenant tenant = tenantRepository.findById(tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Tenant"));

		Project project = Project.builder()
				.tenant(tenant)
				.name(request.name().trim())
				.description(request.description() != null ? request.description().trim() : null)
				.build();

		return ProjectResponse.from(projectRepository.save(project));
	}

	@Transactional
	public ProjectResponse updateProject(UUID tenantId, UUID projectId, UpdateProjectRequest request) {
		Project project = projectRepository.findByIdAndTenant_Id(projectId, tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Project"));

		project.setName(request.name().trim());
		project.setDescription(request.description() != null ? request.description().trim() : null);

		return ProjectResponse.from(projectRepository.save(project));
	}

	@Transactional
	public void deleteProject(UUID tenantId, UUID projectId) {
		Project project = projectRepository.findByIdAndTenant_Id(projectId, tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Project"));
		projectRepository.delete(project);
	}
}
