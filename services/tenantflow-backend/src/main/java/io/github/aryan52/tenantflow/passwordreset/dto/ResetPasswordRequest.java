package io.github.aryan52.tenantflow.passwordreset.dto;

import io.github.aryan52.tenantflow.auth.dto.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
		@NotBlank String token,
		@NotBlank @Size(min = 8, max = 72)
		@Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
		String newPassword
) {
}
