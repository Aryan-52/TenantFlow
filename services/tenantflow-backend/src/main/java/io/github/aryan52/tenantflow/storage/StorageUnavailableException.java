package io.github.aryan52.tenantflow.storage;

/** 503: object storage could not be reached or rejected the operation. Details are logged, not returned. */
public class StorageUnavailableException extends RuntimeException {
	public StorageUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
