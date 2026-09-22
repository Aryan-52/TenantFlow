package io.github.aryan52.tenantflow.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
		@NotBlank @Size(max = 120) String name
) {
}
