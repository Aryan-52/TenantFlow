package io.github.aryan52.tenantflow.user;

public class PasswordUnchangedException extends RuntimeException {

	public PasswordUnchangedException() {
		super("New password must be different from your current password.");
	}
}
