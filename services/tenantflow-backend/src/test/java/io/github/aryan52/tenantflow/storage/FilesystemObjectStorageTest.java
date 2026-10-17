package io.github.aryan52.tenantflow.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemObjectStorageTest {

	@TempDir
	Path root;

	private static InputStream bytes(String s) {
		return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void storesReadsAndDeletesUnderTheRoot() throws IOException {
		FilesystemObjectStorage storage = new FilesystemObjectStorage(root);
		storage.put("tenants/t1/projects/p1/abc", bytes("hello"), 5, "text/plain");
		try (InputStream in = storage.get("tenants/t1/projects/p1/abc")) {
			assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
		}
		assertThat(root.resolve("tenants/t1/projects/p1/abc")).exists();
		storage.delete("tenants/t1/projects/p1/abc");
		assertThat(root.resolve("tenants/t1/projects/p1/abc")).doesNotExist();
		storage.delete("tenants/t1/projects/p1/abc"); // idempotent
	}

	@Test
	void objectsSurviveARestart() throws IOException {
		new FilesystemObjectStorage(root).put("k1", bytes("durable"), 7, "text/plain");
		FilesystemObjectStorage afterRestart = new FilesystemObjectStorage(root);
		try (InputStream in = afterRestart.get("k1")) {
			assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("durable");
		}
	}

	@Test
	void keysCannotEscapeTheRoot() {
		FilesystemObjectStorage storage = new FilesystemObjectStorage(root);
		for (String key : new String[] {"../outside", "a/../../b", "/etc/passwd", "C:/x", "a\b", "a/./b", "", "a//b"}) {
			assertThatThrownBy(() -> storage.put(key, bytes("x"), 1, "text/plain")).as(key).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> storage.get(key)).as(key).isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void aMissingObjectIsReportedAsMissingNotAsAnOutage() {
		FilesystemObjectStorage storage = new FilesystemObjectStorage(root);
		assertThatThrownBy(() -> storage.get("never-stored")).isInstanceOf(StoredObjectMissingException.class);
	}

	@Test
	void aFailedWriteLeavesNoPartialFile() throws IOException {
		FilesystemObjectStorage storage = new FilesystemObjectStorage(root);
		InputStream failing = new InputStream() {
			private int n;

			@Override
			public int read() throws IOException {
				if (n++ > 10) {
					throw new IOException("client disconnected");
				}
				return 'x';
			}
		};
		assertThatThrownBy(() -> storage.put("dir/obj", failing, 100, "text/plain")).isInstanceOf(StorageUnavailableException.class);
		try (Stream<Path> files = Files.walk(root)) {
			assertThat(files.filter(Files::isRegularFile).toList()).isEmpty();
		}
	}

	@Test
	void anUnusableDirectoryIsAnOutageNotAStartupFailureOrALostFile() throws IOException {
		Path notADirectory = Files.writeString(root.resolve("occupied"), "a file where the directory should be");
		FilesystemObjectStorage storage = new FilesystemObjectStorage(notADirectory.resolve("storage"));
		assertThatThrownBy(() -> storage.put("k1", bytes("x"), 1, "text/plain")).isInstanceOf(StorageUnavailableException.class);
		assertThatThrownBy(() -> storage.get("k1")).isInstanceOf(StorageUnavailableException.class);
	}
}
