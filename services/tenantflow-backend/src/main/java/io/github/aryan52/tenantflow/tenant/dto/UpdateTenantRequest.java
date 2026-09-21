package io.github.aryan52.tenantflow.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(
		@NotBlank @Size(max = 160) String name
) {
}
