package io.github.aryan52.tenantflow.storage;

/**
 * 404: the file's metadata exists but its bytes are gone (e.g. storage was wiped). Unlike
 * {@link StorageUnavailableException} this is not temporary, so retrying will not help.
 */
public class StoredObjectMissingException extends RuntimeException {
	public StoredObjectMissingException(String key, Throwable cause) {
		super("Stored object missing: " + key, cause);
	}
}
