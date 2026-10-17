package io.github.aryan52.tenantflow.storage;

import java.io.InputStream;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * AWS SDK v2 implementation for AWS S3 and S3-compatible stores (MinIO). Objects are
 * private (no ACLs, no public URLs); they are only ever read through the authorised
 * download endpoint. Server-side encryption is requested on AWS.
 */
public class S3ObjectStorage implements ObjectStorage {

	private static final Logger log = LoggerFactory.getLogger(S3ObjectStorage.class);

	private final S3Client s3;
	private final String bucket;
	private final boolean awsHosted;

	public S3ObjectStorage(StorageProperties props) {
		var builder = S3Client.builder()
				.region(Region.of(props.region()))
				.credentialsProvider(credentials(props))
				.forcePathStyle(props.pathStyle())
				// Only send/verify checksums when an operation requires them: keeps the client
				// compatible with S3-compatible servers that predate the new default checksums.
				.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
				.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
		if (props.endpoint() != null && !props.endpoint().isBlank()) {
			builder.endpointOverride(URI.create(props.endpoint()));
		}
		this.s3 = builder.build();
		this.bucket = props.bucket();
		this.awsHosted = props.endpoint() == null || props.endpoint().isBlank();
		if (props.createBucket()) {
			ensureBucket();
		}
	}

	private static AwsCredentialsProvider credentials(StorageProperties props) {
		if (props.accessKey() != null && !props.accessKey().isBlank()) {
			return StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
		}
		return DefaultCredentialsProvider.builder().build();
	}

	private void ensureBucket() {
		try {
			s3.headBucket(b -> b.bucket(bucket));
		} catch (NoSuchBucketException ex) {
			s3.createBucket(b -> b.bucket(bucket));
			log.info("Created storage bucket {}", bucket);
		} catch (SdkException ex) {
			// Do not block start-up: uploads report 503 until storage becomes reachable.
			log.warn("Could not verify storage bucket {} ({})", bucket, ex.getClass().getSimpleName());
		}
	}

	@Override
	public void put(String key, InputStream content, long length, String contentType) {
		try {
			s3.putObject(b -> {
				b.bucket(bucket).key(key).contentType(contentType).contentLength(length);
				if (awsHosted) {
					b.serverSideEncryption(ServerSideEncryption.AES256);
				}
			}, RequestBody.fromInputStream(content, length));
		} catch (SdkException ex) {
			throw new StorageUnavailableException("Could not store the file", ex);
		}
	}

	@Override
	public InputStream get(String key) {
		try {
			return s3.getObject(b -> b.bucket(bucket).key(key));
		} catch (NoSuchKeyException ex) {
			throw new StoredObjectMissingException(key, ex);
		} catch (SdkException ex) {
			throw new StorageUnavailableException("Could not read the file", ex);
		}
	}

	@Override
	public void delete(String key) {
		try {
			s3.deleteObject(b -> b.bucket(bucket).key(key));
		} catch (SdkException ex) {
			throw new StorageUnavailableException("Could not delete the file", ex);
		}
	}
}
