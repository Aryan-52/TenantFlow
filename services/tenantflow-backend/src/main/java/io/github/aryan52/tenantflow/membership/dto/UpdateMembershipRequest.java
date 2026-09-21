package io.github.aryan52.tenantflow.membership.dto;

import io.github.aryan52.tenantflow.entity.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateMembershipRequest(
		@NotNull Role role
) {
}
