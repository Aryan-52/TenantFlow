package io.github.aryan52.tenantflow.user;

public class InvalidCurrentPasswordException extends RuntimeException {

	public InvalidCurrentPasswordException() {
		super("Current password is incorrect");
	}
}
