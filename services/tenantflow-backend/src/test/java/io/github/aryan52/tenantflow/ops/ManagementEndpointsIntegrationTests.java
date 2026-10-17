package io.github.aryan52.tenantflow.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Operational endpoints run on a separate management port (never published publicly).
 * The public port only offers status-only liveness/readiness probes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_mgmt;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-management-secret-test-management-secret-test",
		"management.server.port=0",
		"management.health.redis.enabled=false"
})
class ManagementEndpointsIntegrationTests {

	@LocalServerPort int serverPort;
	@LocalManagementPort int managementPort;

	private final HttpClient http = HttpClient.newHttpClient();

	private HttpResponse<String> get(int port, String path) throws Exception {
		return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void publicPortExposesOnlyStatusProbes() throws Exception {
		assertThat(managementPort).isNotEqualTo(serverPort);

		HttpResponse<String> ready = get(serverPort, "/readyz");
		assertThat(ready.statusCode()).isEqualTo(200);
		assertThat(ready.body()).contains("UP").doesNotContain("components").doesNotContain("details");
		assertThat(get(serverPort, "/livez").statusCode()).isEqualTo(200);

		assertThat(get(serverPort, "/actuator/prometheus").statusCode()).isNotEqualTo(200);
		assertThat(get(serverPort, "/actuator/health").statusCode()).isNotEqualTo(200);
	}

	@Test
	void managementPortServesMetricsAndDetailFreeHealth() throws Exception {
		HttpResponse<String> metrics = get(managementPort, "/actuator/prometheus");
		assertThat(metrics.statusCode()).isEqualTo(200);
		assertThat(metrics.body()).contains("jvm_memory_used_bytes").contains("hikaricp_connections");

		HttpResponse<String> health = get(managementPort, "/actuator/health");
		assertThat(health.statusCode()).isEqualTo(200);
		assertThat(health.body()).doesNotContain("components");
	}
}
