package io.github.aryan52.tenantflow.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/**
 * Under the prod profile the backend must refuse to start with storage that would lose
 * uploaded documents (Render's disk is wiped on every deploy) or with an S3 setup that would
 * only fail on the first upload. No cloud credentials needed: this is configuration only.
 */
class ProductionStorageConfigurationTests {

	private static final String KEY = "test-access-key";
	private static final String SECRET = "test-secret-value-never-printed";

	private static StorageProperties prod(String type, String endpoint, String accessKey, String secretKey, boolean persistentDisk) {
		return new StorageProperties(type, null, "tenantflow-attachments", endpoint, "auto", accessKey, secretKey, true, false,
				null, true, persistentDisk);
	}

	@Test
	void theProdProfileTurnsTheChecksOnAndHasNoLocalEndpointDefault() throws Exception {
		Properties prod = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-prod.properties"));
		assertThat(prod.getProperty("tenantflow.storage.require-persistent")).isEqualTo("true");
		assertThat(prod.getProperty("tenantflow.storage.type")).isEqualTo("${TENANTFLOW_STORAGE_TYPE:s3}");
		assertThat(prod.getProperty("tenantflow.storage.endpoint")).isEqualTo("${TENANTFLOW_STORAGE_ENDPOINT:}");
	}

	@Test
	void completeS3CompatibleAndAwsConfigurationsStart() {
		assertThatCode(() -> prod("s3", "https://account.r2.cloudflarestorage.com", KEY, SECRET, false)).doesNotThrowAnyException();
		assertThatCode(() -> prod("s3", "http://minio:9000", KEY, SECRET, false)).doesNotThrowAnyException(); // Docker Compose
		assertThatCode(() -> prod("s3", "", null, null, false)).doesNotThrowAnyException(); // AWS credential chain
		assertThatCode(() -> prod("filesystem", null, null, null, true)).doesNotThrowAnyException(); // persistent disk confirmed
	}

	@Test
	void storageThatLosesFilesIsRefused() {
		assertThatThrownBy(() -> prod("filesystem", null, null, null, false))
				.hasMessageContaining("wipe on every deploy").hasMessageContaining("TENANTFLOW_STORAGE_FILESYSTEM_PERSISTENT=true");
		assertThatThrownBy(() -> prod("memory", null, null, null, false)).hasMessageContaining("tests only");
	}

	@Test
	void incompleteOrLocalS3ConfigurationIsRefusedWithoutRevealingValues() {
		assertThatThrownBy(() -> prod("s3", "https://s3.example.com", null, null, false))
				.hasMessageContaining("TENANTFLOW_STORAGE_ACCESS_KEY and TENANTFLOW_STORAGE_SECRET_KEY are required");
		assertThatThrownBy(() -> prod("s3", "https://s3.example.com", KEY, null, false))
				.hasMessageContaining("Set both").hasMessageNotContaining(KEY);
		assertThatThrownBy(() -> prod("s3", "", null, SECRET, false))
				.hasMessageContaining("Set both").hasMessageNotContaining(SECRET);
		assertThatThrownBy(() -> prod("s3", "http://localhost:9000", KEY, SECRET, false))
				.hasMessageContaining("points at this machine").hasMessageNotContaining(SECRET);
	}

	@Test
	void developmentAndTestsAreUnaffected() {
		// require-persistent is false outside the prod profile.
		assertThatCode(() -> new StorageProperties("memory", null, null, null, null, null, null, false, false, null, false, false))
				.doesNotThrowAnyException();
		assertThatCode(() -> new StorageProperties("filesystem", null, null, "http://localhost:9000", null, null, null, false, false,
				null, false, false)).doesNotThrowAnyException();
	}
}
