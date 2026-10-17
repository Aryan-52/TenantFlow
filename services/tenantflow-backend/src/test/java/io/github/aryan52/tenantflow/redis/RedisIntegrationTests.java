package io.github.aryan52.tenantflow.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.Project;
import io.github.aryan52.tenantflow.entity.Role;
import io.github.aryan52.tenantflow.entity.Tenant;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.ratelimit.RedisRateLimiter;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import io.github.aryan52.tenantflow.repository.TaskRepository;
import io.github.aryan52.tenantflow.repository.TenantRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.security.JwtService;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Rate limiting and tenant-scoped caching against a real Redis. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_redis;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-redis-secret-test-redis-secret-test-redis-secret",
		"spring.cache.type=redis",
		"tenantflow.rate-limit.store=redis"
})
@AutoConfigureMockMvc
class RedisIntegrationTests {

	@Container
	@ServiceConnection(name = "redis")
	static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

	@Autowired private MockMvc mockMvc;
	@Autowired private StringRedisTemplate redis;
	@Autowired private UserRepository userRepository;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private MembershipRepository membershipRepository;
	@Autowired private ProjectRepository projectRepository;
	@Autowired private TaskRepository taskRepository;
	@Autowired private JwtService jwtService;

	private Tenant tenantA;
	private Tenant tenantB;
	private Project projectA;
	private String tokenA;
	private String tokenB;

	@BeforeEach
	void setup() {
		redis.getConnectionFactory().getConnection().serverCommands().flushAll();
		taskRepository.deleteAll();
		projectRepository.deleteAll();
		membershipRepository.deleteAll();
		tenantRepository.deleteAll();
		userRepository.deleteAll();
		User a = userRepository.save(User.builder().email("a@example.com").name("A").passwordHash("h").build());
		User b = userRepository.save(User.builder().email("b@example.com").name("B").passwordHash("h").build());
		tenantA = tenantRepository.save(Tenant.builder().name("A").slug("a").build());
		tenantB = tenantRepository.save(Tenant.builder().name("B").slug("b").build());
		membershipRepository.save(Membership.builder().user(a).tenant(tenantA).role(Role.OWNER).build());
		membershipRepository.save(Membership.builder().user(b).tenant(tenantB).role(Role.OWNER).build());
		projectA = projectRepository.save(Project.builder().tenant(tenantA).name("PA").build());
		tokenA = jwtService.generateToken(a);
		tokenB = jwtService.generateToken(b);
	}

	private String statsKey(Tenant t) {
		return "tenantflow:cache:v2:tenantStats::" + t.getId();
	}

	@Test
	void rateLimitCountersAreSharedAcrossInstancesAndExpire() {
		RedisRateLimiter instance1 = new RedisRateLimiter(redis);
		RedisRateLimiter instance2 = new RedisRateLimiter(redis);

		assertThat(instance1.tryConsume("login:ip:1.2.3.4", 2, Duration.ofMinutes(1)).allowed()).isTrue();
		assertThat(instance2.tryConsume("login:ip:1.2.3.4", 2, Duration.ofMinutes(1)).allowed()).isTrue();
		assertThat(instance1.tryConsume("login:ip:1.2.3.4", 2, Duration.ofMinutes(1)).allowed()).isFalse();

		Long ttl = redis.getExpire("tenantflow:ratelimit:login:ip:1.2.3.4");
		assertThat(ttl).isBetween(1L, 60L);
	}

	@Test
	void dashboardStatsAreCachedInRedisPerTenantAndEvictedOnChange() throws Exception {
		mockMvc.perform(get("/api/tenants/" + tenantA.getId() + "/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(jsonPath("$.totalProjects").value(1));
		mockMvc.perform(get("/api/tenants/" + tenantB.getId() + "/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
				.andExpect(jsonPath("$.totalProjects").value(0));

		assertThat(redis.hasKey(statsKey(tenantA))).isTrue();
		assertThat(redis.hasKey(statsKey(tenantB))).isTrue();
		assertThat(redis.opsForValue().get(statsKey(tenantA))).contains("\"totalProjects\":1");
		assertThat(redis.getExpire(statsKey(tenantA))).isPositive();

		mockMvc.perform(post("/api/tenants/" + tenantA.getId() + "/projects/" + projectA.getId() + "/tasks")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"T\",\"status\":\"TODO\",\"priority\":\"LOW\"}"))
				.andExpect(status().isCreated());

		assertThat(redis.hasKey(statsKey(tenantA))).isFalse();
		assertThat(redis.hasKey(statsKey(tenantB))).isTrue();
		mockMvc.perform(get("/api/tenants/" + tenantA.getId() + "/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(jsonPath("$.totalTasks").value(1));
	}
}
