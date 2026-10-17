package io.github.aryan52.tenantflow.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.Project;
import io.github.aryan52.tenantflow.entity.Role;
import io.github.aryan52.tenantflow.entity.Tenant;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import io.github.aryan52.tenantflow.repository.TenantRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.security.JwtService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * Upload / download / delete through the real S3 client against MinIO. (The official
 * minio/minio images are no longer published; Bitnami's legacy MinIO build is used for
 * local development and tests only.)
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_minio;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-minio-secret-test-minio-secret-test-minio-secret",
		"tenantflow.storage.type=s3",
		"tenantflow.storage.bucket=tenantflow-test",
		"tenantflow.storage.path-style=true",
		"tenantflow.storage.create-bucket=true"
})
@AutoConfigureMockMvc
class MinioStorageIntegrationTests {

	private static final String USER = "minio-test-user";
	private static final String PASSWORD = "minio-test-password";

	@Container
	static final GenericContainer<?> MINIO = new GenericContainer<>("bitnamilegacy/minio:2025.7.23")
			.withEnv("MINIO_ROOT_USER", USER)
			.withEnv("MINIO_ROOT_PASSWORD", PASSWORD)
			.withExposedPorts(9000)
			.waitingFor(Wait.forHttp("/minio/health/live").forPort(9000).withStartupTimeout(Duration.ofMinutes(2)));

	@DynamicPropertySource
	static void storage(DynamicPropertyRegistry registry) {
		registry.add("tenantflow.storage.endpoint", MinioStorageIntegrationTests::endpoint);
		registry.add("tenantflow.storage.access-key", () -> USER);
		registry.add("tenantflow.storage.secret-key", () -> PASSWORD);
	}

	private static String endpoint() {
		return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
	}

	@Autowired private MockMvc mockMvc;
	@Autowired private UserRepository userRepository;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private MembershipRepository membershipRepository;
	@Autowired private ProjectRepository projectRepository;
	@Autowired private JwtService jwtService;

	@Test
	void filesRoundTripThroughMinioAndStayPrivate() throws Exception {
		User u = userRepository.save(User.builder().email("m@example.com").name("M").passwordHash("h").build());
		Tenant t = tenantRepository.save(Tenant.builder().name("T").slug("minio-t").build());
		membershipRepository.save(Membership.builder().user(u).tenant(t).role(Role.OWNER).build());
		Project p = projectRepository.save(Project.builder().tenant(t).name("P").build());
		String token = "Bearer " + jwtService.generateToken(u);
		String base = "/api/tenants/" + t.getId() + "/projects/" + p.getId() + "/attachments";
		byte[] pdf = "%PDF-1.7\nstored in minio".getBytes(StandardCharsets.US_ASCII);

		String body = mockMvc.perform(multipart(base).file(new MockMultipartFile("file", "doc.pdf", "application/pdf", pdf))
						.header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");

		mockMvc.perform(get(base + "/" + id + "/download").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk())
				.andExpect(content().bytes(pdf));

		S3Client s3 = S3Client.builder().endpointOverride(URI.create(endpoint())).region(Region.US_EAST_1).forcePathStyle(true)
				.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(USER, PASSWORD))).build();
		String key = "tenants/" + t.getId() + "/projects/" + p.getId() + "/" + id;
		assertThat(s3.getObjectAsBytes(b -> b.bucket("tenantflow-test").key(key)).asByteArray()).isEqualTo(pdf);

		// Objects are private: anonymous access to the object URL is refused.
		var anonymous = java.net.http.HttpClient.newHttpClient().send(
				java.net.http.HttpRequest.newBuilder(URI.create(endpoint() + "/tenantflow-test/" + key)).GET().build(),
				java.net.http.HttpResponse.BodyHandlers.ofString());
		assertThat(anonymous.statusCode()).isEqualTo(403);

		mockMvc.perform(delete(base + "/" + id).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isNoContent());
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> s3.headObject(b -> b.bucket("tenantflow-test").key(key)))
				.isInstanceOfAny(NoSuchKeyException.class, software.amazon.awssdk.services.s3.model.S3Exception.class);
	}
}
