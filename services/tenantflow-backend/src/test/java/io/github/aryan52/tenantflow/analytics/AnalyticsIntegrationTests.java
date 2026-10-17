package io.github.aryan52.tenantflow.analytics;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_analytics;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-analytics-secret-test-analytics-secret-test-anal"
})
@AutoConfigureMockMvc
class AnalyticsIntegrationTests extends AnalyticsScenario {

	@Override
	protected void clean(JdbcTemplate jdbc) {
		for (String table : new String[] {"task_comments", "attachments", "notifications", "activity_events", "idempotency_keys",
				"tasks", "projects", "memberships", "tenants", "users"}) {
			jdbc.execute("delete from " + table);
		}
	}
}
