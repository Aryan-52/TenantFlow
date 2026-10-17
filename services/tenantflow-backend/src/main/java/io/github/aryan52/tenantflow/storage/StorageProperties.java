package io.github.aryan52.tenantflow.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * {@code tenantflow.storage.*}. Credentials and bucket are environment-driven. Leave the
 * access key empty on AWS to use the default credential chain (instance/task role).
 *
 * @param type          {@code filesystem} (local development default), {@code s3} (MinIO in Docker,
 *                      AWS S3 in production) or {@code memory} (tests only)
 * @param directory     root directory for {@code filesystem} storage
 * @param endpoint      S3 endpoint override, e.g. http://localhost:9000 for MinIO; empty for AWS
 * @param pathStyle     true for MinIO
 * @param createBucket  create the bucket at start-up if missing (local development only)
 * @param maxFileSize   per-file upload limit
 */
@ConfigurationProperties(prefix = "tenantflow.storage")
public record StorageProperties(
		String type,
		String directory,
		String bucket,
		String endpoint,
		String region,
		String accessKey,
		String secretKey,
		boolean pathStyle,
		boolean createBucket,
		DataSize maxFileSize
) {
	public StorageProperties {
		type = type == null || type.isBlank() ? "filesystem" : type;
		directory = directory == null || directory.isBlank()
				? java.nio.file.Path.of(System.getProperty("user.home"), ".tenantflow", "storage").toString() : directory;
		bucket = bucket == null || bucket.isBlank() ? "tenantflow-attachments" : bucket;
		region = region == null || region.isBlank() ? "us-east-1" : region;
		maxFileSize = maxFileSize == null ? DataSize.ofMegabytes(10) : maxFileSize;
	}
}
