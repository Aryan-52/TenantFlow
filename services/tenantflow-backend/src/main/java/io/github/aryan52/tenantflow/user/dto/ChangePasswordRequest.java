package io.github.aryan52.tenantflow.user.dto;

import io.github.aryan52.tenantflow.auth.dto.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
		@NotBlank String currentPassword,
		@NotBlank @Size(min = 8, max = 72)
		@Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
		String newPassword
) {
}
