package io.github.aryan52.tenantflow.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.Role;
import io.github.aryan52.tenantflow.entity.Tenant;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.TenantRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.security.JwtService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Redis configured but unreachable (closed port): the application keeps working from
 * PostgreSQL, and rate limiting stays enforced through the in-memory fallback.
 */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_redis_down;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-redis-down-secret-test-redis-down-secret-test",
		"spring.data.redis.host=127.0.0.1",
		"spring.data.redis.port=1",
		"spring.cache.type=redis",
		"tenantflow.rate-limit.enabled=true",
		"tenantflow.rate-limit.store=redis",
		"tenantflow.rate-limit.rules.login.limit=2",
		"tenantflow.rate-limit.rules.login.window=1m"
})
@AutoConfigureMockMvc
class RedisUnavailableIntegrationTests {

	@Autowired private MockMvc mockMvc;
	@Autowired private UserRepository userRepository;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private MembershipRepository membershipRepository;
	@Autowired private JwtService jwtService;
	@Autowired private MeterRegistry meterRegistry;

	@Test
	void dashboardFallsBackToTheDatabase() throws Exception {
		User u = userRepository.save(User.builder().email("down@example.com").name("D").passwordHash("h").build());
		Tenant t = tenantRepository.save(Tenant.builder().name("T").slug("down").build());
		membershipRepository.save(Membership.builder().user(u).tenant(t).role(Role.OWNER).build());

		mockMvc.perform(get("/api/tenants/" + t.getId() + "/dashboard")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(u)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalMembers").value(1));
		assertThat(meterRegistry.find("tenantflow.cache.errors").counters()).isNotEmpty();
	}

	@Test
	void rateLimitingIsStillEnforced() throws Exception {
		for (int i = 0; i < 2; i++) {
			mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
							.content("{\"email\":\"x@example.com\",\"password\":\"Whatever1!\"}"))
					.andExpect(status().isUnauthorized());
		}
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"x@example.com\",\"password\":\"Whatever1!\"}"))
				.andExpect(status().isTooManyRequests());
		assertThat(meterRegistry.counter("tenantflow.ratelimit.fallback").count()).isGreaterThanOrEqualTo(3);
	}
}
