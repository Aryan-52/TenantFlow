package io.github.aryan52.tenantflow.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank @Email @Size(max = 320) String email,
		@NotBlank @Size(min = 8, max = 72)
		@Pattern(
				regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
				message = "Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character"
		)
		String password,
		@NotBlank @Size(max = 120) String name
) {
}
