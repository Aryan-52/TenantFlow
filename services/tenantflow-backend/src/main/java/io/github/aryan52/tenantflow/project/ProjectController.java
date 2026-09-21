package io.github.aryan52.tenantflow.project;

import io.github.aryan52.tenantflow.project.dto.CreateProjectRequest;
import io.github.aryan52.tenantflow.project.dto.ProjectResponse;
import io.github.aryan52.tenantflow.project.dto.UpdateProjectRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/{tenantId}/projects")
@RequiredArgsConstructor
public class ProjectController {

	private final ProjectService projectService;

	@GetMapping
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public List<ProjectResponse> getProjects(@PathVariable UUID tenantId) {
		return projectService.getProjects(tenantId);
	}

	@GetMapping("/{projectId}")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public ProjectResponse getProject(
			@PathVariable UUID tenantId,
			@PathVariable UUID projectId
	) {
		return projectService.getProject(tenantId, projectId);
	}

	@PostMapping
	@PreAuthorize("@tenantSecurity.hasRole(#tenantId, 'ADMIN', 'OWNER')")
	public ResponseEntity<ProjectResponse> createProject(
			@PathVariable UUID tenantId,
			@Valid @RequestBody CreateProjectRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(projectService.createProject(tenantId, request));
	}

	@PutMapping("/{projectId}")
	@PreAuthorize("@tenantSecurity.hasRole(#tenantId, 'ADMIN', 'OWNER')")
	public ProjectResponse updateProject(
			@PathVariable UUID tenantId,
			@PathVariable UUID projectId,
			@Valid @RequestBody UpdateProjectRequest request
	) {
		return projectService.updateProject(tenantId, projectId, request);
	}

	@DeleteMapping("/{projectId}")
	@PreAuthorize("@tenantSecurity.hasRole(#tenantId, 'ADMIN', 'OWNER')")
	public ResponseEntity<Void> deleteProject(
			@PathVariable UUID tenantId,
			@PathVariable UUID projectId
	) {
		projectService.deleteProject(tenantId, projectId);
		return ResponseEntity.noContent().build();
	}
}
