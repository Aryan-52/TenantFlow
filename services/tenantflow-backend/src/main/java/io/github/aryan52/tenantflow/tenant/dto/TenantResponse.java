package io.github.aryan52.tenantflow.tenant.dto;

import io.github.aryan52.tenantflow.entity.Role;
import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
		UUID id,
		String name,
		String slug,
		Instant createdAt,
		Instant updatedAt,
		Role myRole
) {
}
