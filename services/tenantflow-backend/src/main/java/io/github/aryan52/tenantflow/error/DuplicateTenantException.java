package io.github.aryan52.tenantflow.error;

public class DuplicateTenantException extends RuntimeException {
	public DuplicateTenantException(String slug) {
		super("Tenant with slug '" + slug + "' already exists");
	}
}
