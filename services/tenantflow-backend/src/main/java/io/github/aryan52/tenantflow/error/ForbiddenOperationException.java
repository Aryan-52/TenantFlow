package io.github.aryan52.tenantflow.error;

/**
 * 403 for rules that depend on the target resource (e.g. the role hierarchy), as opposed to
 * plain role checks which {@code @PreAuthorize} rejects with a generic "Access denied".
 */
public class ForbiddenOperationException extends RuntimeException {
	public ForbiddenOperationException(String message) {
		super(message);
	}
}
