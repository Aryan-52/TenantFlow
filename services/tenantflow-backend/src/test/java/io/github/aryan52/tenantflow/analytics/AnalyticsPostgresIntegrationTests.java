package io.github.aryan52.tenantflow.analytics;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The same scenario on real PostgreSQL with the Flyway schema (incl. V15 completed_at). */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:postgresql://must-be-overridden-by-testcontainers.invalid:1/none",
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate",
		"tenantflow.jwt.secret=test-analytics-pg-secret-test-analytics-pg-secret-xx"
})
@AutoConfigureMockMvc
class AnalyticsPostgresIntegrationTests extends AnalyticsScenario {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

	@Override
	protected void clean(JdbcTemplate jdbc) {
		jdbc.execute("truncate task_comments, attachments, notifications, activity_events, idempotency_keys, tasks, projects, memberships, tenants, users cascade");
	}
}
