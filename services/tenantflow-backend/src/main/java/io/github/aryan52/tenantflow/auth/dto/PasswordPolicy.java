package io.github.aryan52.tenantflow.auth.dto;

/**
 * Central place for the password complexity rule so every endpoint that accepts a new
 * password (registration, password change, ...) enforces the exact same policy.
 */
public final class PasswordPolicy {

	public static final String REGEX =
			"^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$";

	public static final String MESSAGE =
			"Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character";

	private PasswordPolicy() {
	}
}
