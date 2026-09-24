package io.github.aryan52.tenantflow.refreshtoken;

public class InvalidRefreshTokenException extends RuntimeException {

	public InvalidRefreshTokenException() {
		super("Your session has expired. Please sign in again.");
	}
}
