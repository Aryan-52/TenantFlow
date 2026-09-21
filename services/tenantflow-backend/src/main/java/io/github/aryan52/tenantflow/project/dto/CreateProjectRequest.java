package io.github.aryan52.tenantflow.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
		@NotBlank @Size(max = 160) String name,
		@Size(max = 2000) String description
) {}
