package io.github.aryan52.tenantflow.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.Project;
import io.github.aryan52.tenantflow.entity.Role;
import io.github.aryan52.tenantflow.entity.Tenant;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import io.github.aryan52.tenantflow.repository.TaskRepository;
import io.github.aryan52.tenantflow.repository.TenantRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.security.JwtService;
import io.github.aryan52.tenantflow.tenant.dto.TenantStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tenant-scoped caching of dashboard counts (in-memory cache here; the Redis-backed variant
 * is covered by RedisIntegrationTests): entries are keyed per tenant, never leak across
 * tenants, and are evicted after every mutation so users never see stale counts.
 */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_cache;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-cache-secret-test-cache-secret-test-cache-secret"
})
@AutoConfigureMockMvc
class TenantStatsCacheIntegrationTests {

	@Autowired private MockMvc mockMvc;
	@Autowired private CacheManager cacheManager;
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
		cacheManager.getCache(CacheConfig.TENANT_STATS).clear();
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
		projectRepository.save(Project.builder().tenant(tenantB).name("PB1").build());
		projectRepository.save(Project.builder().tenant(tenantB).name("PB2").build());
		tokenA = jwtService.generateToken(a);
		tokenB = jwtService.generateToken(b);
	}

	private Cache cache() {
		return cacheManager.getCache(CacheConfig.TENANT_STATS);
	}

	private TenantStats cached(Tenant t) {
		Cache.ValueWrapper w = cache().get(t.getId().toString());
		return w == null ? null : (TenantStats) w.get();
	}

	@Test
	void statsAreCachedPerTenantAndNeverShared() throws Exception {
		mockMvc.perform(get("/api/tenants/" + tenantA.getId() + "/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(jsonPath("$.totalProjects").value(1));
		mockMvc.perform(get("/api/tenants/" + tenantB.getId() + "/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
				.andExpect(jsonPath("$.totalProjects").value(2));

		assertThat(cached(tenantA).totalProjects()).isEqualTo(1);
		assertThat(cached(tenantB).totalProjects()).isEqualTo(2);

		// B cannot read A's dashboard - the authorization check runs before any cache lookup.
		mockMvc.perform(get("/api/tenants/" + tenantA.getId() + "/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
				.andExpect(status().isForbidden());
	}

	@Test
	void mutationsEvictOnlyTheAffectedTenantAndCountsAreFresh() throws Exception {
		mockMvc.perform(get("/api/tenants/" + tenantA.getId() + "/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA));
		mockMvc.perform(get("/api/tenants/" + tenantB.getId() + "/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB));
		assertThat(cached(tenantA)).isNotNull();

		mockMvc.perform(post("/api/tenants/" + tenantA.getId() + "/projects/" + projectA.getId() + "/tasks")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"New\",\"status\":\"TODO\",\"priority\":\"LOW\"}"))
				.andExpect(status().isCreated());

		assertThat(cached(tenantA)).isNull();
		assertThat(cached(tenantB)).isNotNull();
		mockMvc.perform(get("/api/tenants/" + tenantA.getId() + "/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(jsonPath("$.totalTasks").value(1))
				.andExpect(jsonPath("$.todoTasks").value(1));

		mockMvc.perform(delete("/api/tenants/" + tenantA.getId() + "/projects/" + projectA.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/tenants/" + tenantA.getId() + "/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(jsonPath("$.totalProjects").value(0))
				.andExpect(jsonPath("$.totalTasks").value(0));
	}

	@Test
	void aFailedMutationDoesNotEvict() throws Exception {
		mockMvc.perform(get("/api/tenants/" + tenantA.getId() + "/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA));
		mockMvc.perform(post("/api/tenants/" + tenantA.getId() + "/projects/" + projectA.getId() + "/tasks")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"x\",\"status\":\"TODO\",\"priority\":\"LOW\",\"assigneeId\":\"" + java.util.UUID.randomUUID() + "\"}"))
				.andExpect(status().isBadRequest());
		assertThat(cached(tenantA)).isNotNull();
	}
}
