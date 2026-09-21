package io.github.aryan52.tenantflow.membership.dto;

import io.github.aryan52.tenantflow.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddMembershipRequest(
		@NotBlank @Email String email,
		@NotNull Role role
) {
}
