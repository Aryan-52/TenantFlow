package io.github.aryan52.tenantflow.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aryan52.tenantflow.entity.Project;
import io.github.aryan52.tenantflow.entity.Tenant;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import io.github.aryan52.tenantflow.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Boots the whole application against a disposable PostgreSQL with Flyway enabled and
 * {@code ddl-auto=validate} (the production settings), so any drift between the entities
 * and the migrations fails the build.
 *
 * <p>The datasource URL is deliberately pointed at an unreachable host: the container's
 * {@code @ServiceConnection} overrides it, and if that wiring ever broke the test would fail
 * instead of silently migrating the developer's real database on localhost:5432.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:postgresql://must-be-overridden-by-testcontainers.invalid:1/none",
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate",
		"tenantflow.jwt.secret=test-postgres-validation-secret-test-postgres-validation"
})
class PostgresSchemaValidationTests {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired private TenantRepository tenantRepository;
	@Autowired private ProjectRepository projectRepository;

	@Test
	void entitiesMatchTheMigratedSchemaAndVersionsIncrement() {
		Tenant tenant = tenantRepository.save(Tenant.builder().name("T").slug("pg-validation").build());
		Project project = projectRepository.saveAndFlush(Project.builder().tenant(tenant).name("P").build());
		assertThat(project.getVersion()).isZero();

		project.setName("P2");
		assertThat(projectRepository.saveAndFlush(project).getVersion()).isEqualTo(1);
	}
}
