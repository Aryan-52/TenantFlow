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
 * @param requirePersistent  set by the prod profile: refuse to start with storage that loses
 *                      files (memory, or a filesystem not confirmed as persistent) or with an
 *                      incomplete S3 configuration, instead of failing on the first upload
 * @param filesystemPersistent  confirms that {@code directory} is on a persistent disk (e.g. a
 *                      Render persistent disk); without it a container filesystem is assumed to
 *                      be wiped on every deploy
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
		DataSize maxFileSize,
		boolean requirePersistent,
		boolean filesystemPersistent
) {
	public StorageProperties {
		type = type == null || type.isBlank() ? "filesystem" : type;
		directory = directory == null || directory.isBlank()
				? java.nio.file.Path.of(System.getProperty("user.home"), ".tenantflow", "storage").toString() : directory;
		bucket = bucket == null || bucket.isBlank() ? "tenantflow-attachments" : bucket;
		region = region == null || region.isBlank() ? "us-east-1" : region;
		maxFileSize = maxFileSize == null ? DataSize.ofMegabytes(10) : maxFileSize;
		if (requirePersistent) {
			java.util.List<String> problems = productionProblems(type, endpoint, accessKey, secretKey, filesystemPersistent);
			if (!problems.isEmpty()) {
				// Names settings only; never values (keys and secrets live here).
				throw new IllegalStateException("Attachment storage is not configured for production: "
						+ String.join(" ", problems) + " See docs/OPERATIONS.md, \"Production deployment\".");
			}
		}
	}

	private static java.util.List<String> productionProblems(String type, String endpoint, String accessKey,
			String secretKey, boolean filesystemPersistent) {
		java.util.List<String> problems = new java.util.ArrayList<>();
		switch (type) {
			case "s3" -> {
				boolean hasKey = accessKey != null && !accessKey.isBlank();
				boolean hasSecret = secretKey != null && !secretKey.isBlank();
				boolean customEndpoint = endpoint != null && !endpoint.isBlank();
				if (customEndpoint && pointsAtThisMachine(endpoint)) {
					problems.add("TENANTFLOW_STORAGE_ENDPOINT points at this machine (localhost); set your provider's S3 endpoint, or leave it empty for AWS S3.");
				}
				if (hasKey != hasSecret) {
					problems.add("Set both TENANTFLOW_STORAGE_ACCESS_KEY and TENANTFLOW_STORAGE_SECRET_KEY, or neither (AWS credential chain).");
				} else if (customEndpoint && !hasKey) {
					problems.add("TENANTFLOW_STORAGE_ACCESS_KEY and TENANTFLOW_STORAGE_SECRET_KEY are required for an S3-compatible provider (TENANTFLOW_STORAGE_ENDPOINT is set).");
				}
			}
			case "filesystem" -> {
				if (!filesystemPersistent) {
					problems.add("TENANTFLOW_STORAGE_TYPE=filesystem keeps files on the container's disk, which hosting platforms such as Render wipe on every deploy. Use TENANTFLOW_STORAGE_TYPE=s3, or mount a persistent disk at TENANTFLOW_STORAGE_DIR and set TENANTFLOW_STORAGE_FILESYSTEM_PERSISTENT=true.");
				}
			}
			case "memory" -> problems.add("TENANTFLOW_STORAGE_TYPE=memory loses every file on restart; it is for tests only.");
			default -> problems.add("Unknown TENANTFLOW_STORAGE_TYPE (use s3 or filesystem).");
		}
		return problems;
	}

	private static boolean pointsAtThisMachine(String endpoint) {
		try {
			String host = java.net.URI.create(endpoint.trim()).getHost();
			return host != null && (host.equalsIgnoreCase("localhost") || host.startsWith("127.") || host.equals("[::1]") || host.equals("0.0.0.0"));
		} catch (IllegalArgumentException e) {
			return false; // A malformed endpoint is reported by the S3 client itself.
		}
	}
}
