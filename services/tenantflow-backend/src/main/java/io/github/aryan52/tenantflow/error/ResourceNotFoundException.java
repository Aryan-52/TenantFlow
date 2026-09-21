package io.github.aryan52.tenantflow.error;

public class ResourceNotFoundException extends RuntimeException {
	public ResourceNotFoundException(String resource) {
		super(resource + " not found");
	}
}
