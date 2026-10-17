package io.github.aryan52.tenantflow.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.aryan52.tenantflow.audit.AuditAction;
import io.github.aryan52.tenantflow.audit.AuditEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_rate_limit;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-rate-limit-secret-test-rate-limit-secret-test-rate",
		"tenantflow.rate-limit.enabled=true",
		"tenantflow.rate-limit.store=memory",
		"tenantflow.rate-limit.rules.login.limit=3",
		"tenantflow.rate-limit.rules.login.window=1m",
		"tenantflow.rate-limit.rules.forgot-password.limit=1",
		"tenantflow.rate-limit.rules.forgot-password.window=15m"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class RateLimitIntegrationTests {

	@Autowired private MockMvc mockMvc;
	@Autowired private AuditEventRepository auditEventRepository;
	@Autowired private MeterRegistry meterRegistry;

	@BeforeEach
	void clean() {
		auditEventRepository.deleteAll();
	}

	private ResultActions login(String ip) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
				.with(request -> {
					request.setRemoteAddr(ip);
					return request;
				})
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"nobody@example.com\",\"password\":\"Whatever1!\"}"));
	}

	@Test
	void loginIsLimitedPerClientIpWithRetryAfter() throws Exception {
		for (int i = 0; i < 3; i++) {
			login("10.0.0.1").andExpect(status().isUnauthorized());
		}
		login("10.0.0.1")
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(jsonPath("$.status").value(429))
				.andExpect(jsonPath("$.message").value("Too many requests. Please wait and try again."))
				.andExpect(jsonPath("$.requestId").exists());

		// A different client is unaffected.
		login("10.0.0.2").andExpect(status().isUnauthorized());
	}

	@Test
	void rejectionsAreAuditedOncePerWindowAndCounted() throws Exception {
		for (int i = 0; i < 8; i++) {
			login("10.0.0.9");
		}
		assertThat(auditEventRepository.findByAction(AuditAction.RATE_LIMIT_EXCEEDED)).singleElement()
				.satisfies(e -> assertThat(e.getDetails()).isEqualTo("rule=login subject=ip:10.0.0.9"));
		assertThat(meterRegistry.counter("tenantflow.ratelimit.rejected", "rule", "login").count()).isEqualTo(5);
	}

	@Test
	void rateLimitingRunsBeforeTheEndpointDoesAnyWork() throws Exception {
		mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"someone@example.com\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"someone@example.com\"}"))
				.andExpect(status().isTooManyRequests());
	}
}
