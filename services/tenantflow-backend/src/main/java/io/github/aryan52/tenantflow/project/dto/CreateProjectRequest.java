package io.github.aryan52.tenantflow.project.dto;

import io.github.aryan52.tenantflow.entity.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code status} is optional and defaults to ACTIVE. */
public record CreateProjectRequest(
		@NotBlank @Size(max = 160) String name,
		@Size(max = 2000) String description,
		ProjectStatus status
) {}
