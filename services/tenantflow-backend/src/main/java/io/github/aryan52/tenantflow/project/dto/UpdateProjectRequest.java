package io.github.aryan52.tenantflow.project.dto;

import io.github.aryan52.tenantflow.entity.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code status} is optional; when omitted the project's current status is kept. */
public record UpdateProjectRequest(
		@NotBlank @Size(max = 160) String name,
		@Size(max = 2000) String description,
		ProjectStatus status
) {}
