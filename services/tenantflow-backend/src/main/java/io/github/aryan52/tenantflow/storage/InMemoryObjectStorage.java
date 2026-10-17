package io.github.aryan52.tenantflow.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Test-only storage ({@code tenantflow.storage.type=memory}); never use in a shared environment. */
public class InMemoryObjectStorage implements ObjectStorage {

	private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

	@Override
	public void put(String key, InputStream content, long length, String contentType) {
		try {
			objects.put(key, content.readAllBytes());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public InputStream get(String key) {
		byte[] bytes = objects.get(key);
		if (bytes == null) {
			throw new StoredObjectMissingException(key, null);
		}
		return new ByteArrayInputStream(bytes);
	}

	@Override
	public void delete(String key) {
		objects.remove(key);
	}

	public Set<String> keys() {
		return Set.copyOf(objects.keySet());
	}

	public void clear() {
		objects.clear();
	}
}
