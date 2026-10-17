package io.github.aryan52.tenantflow.error;

/** 409: the request is valid but conflicts with the current state (e.g. a stale version). */
public class ConflictException extends RuntimeException {
	public ConflictException(String message) {
		super(message);
	}
}
