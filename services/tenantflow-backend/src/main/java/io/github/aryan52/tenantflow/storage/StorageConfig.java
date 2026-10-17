package io.github.aryan52.tenantflow.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

	/** Lazy: the application starts (and everything except attachments works) without storage. */
	@Bean
	@Lazy
	public ObjectStorage objectStorage(StorageProperties properties) {
		return switch (properties.type()) {
			case "memory" -> new InMemoryObjectStorage();
			case "filesystem" -> new FilesystemObjectStorage(java.nio.file.Path.of(properties.directory()));
			case "s3" -> new S3ObjectStorage(properties);
			default -> throw new IllegalStateException("Unknown tenantflow.storage.type: " + properties.type());
		};
	}
}
