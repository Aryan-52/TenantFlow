package io.github.aryan52.tenantflow.error;

public class DuplicateMembershipException extends RuntimeException {
	public DuplicateMembershipException(String email) {
		super("User '" + email + "' is already a member of this tenant");
	}
}
