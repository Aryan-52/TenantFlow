package io.github.aryan52.tenantflow.auth;

public class DuplicateEmailException extends RuntimeException {

	public DuplicateEmailException() {
		super("An account with this email already exists. Please sign in instead.");
	}
}
