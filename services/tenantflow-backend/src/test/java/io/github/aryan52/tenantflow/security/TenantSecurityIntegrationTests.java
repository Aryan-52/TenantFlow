package io.github.aryan52.tenantflow.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.Role;
import io.github.aryan52.tenantflow.entity.Tenant;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.TenantRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_security;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.show-sql=false",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-auth-secret-test-auth-secret-test-auth-secret",
		"tenantflow.jwt.expiration-seconds=3600"
})
@AutoConfigureMockMvc
@Import(TenantSecurityIntegrationTests.TestTenantControllerConfig.class)
class TenantSecurityIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TenantRepository tenantRepository;

	@Autowired
	private MembershipRepository membershipRepository;

	@Autowired
	private JwtService jwtService;

	private User user1;
	private User user2;
	private Tenant tenant1;
	private Tenant tenant2;
	private String user1Token;
	private String user2Token;

	@BeforeEach
	void setupData() {
		membershipRepository.deleteAll();
		tenantRepository.deleteAll();
		userRepository.deleteAll();

		user1 = userRepository.save(User.builder().email("user1@example.com").name("User 1").passwordHash("hash").build());
		user2 = userRepository.save(User.builder().email("user2@example.com").name("User 2").passwordHash("hash").build());

		tenant1 = tenantRepository.save(Tenant.builder().slug("tenant-1").name("Tenant 1").build());
		tenant2 = tenantRepository.save(Tenant.builder().slug("tenant-2").name("Tenant 2").build());

		// User1 is OWNER of Tenant1
		membershipRepository.save(Membership.builder().user(user1).tenant(tenant1).role(Role.OWNER).build());
		// User1 is MEMBER of Tenant2
		membershipRepository.save(Membership.builder().user(user1).tenant(tenant2).role(Role.MEMBER).build());

		// User2 is only MEMBER of Tenant2
		membershipRepository.save(Membership.builder().user(user2).tenant(tenant2).role(Role.MEMBER).build());

		user1Token = jwtService.generateToken(user1);
		user2Token = jwtService.generateToken(user2);
	}

	@Test
	void unauthenticatedAccessIsRejected() throws Exception {
		mockMvc.perform(get("/api/test-tenants/" + tenant1.getId()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void authenticatedAccessToOwnTenantSucceeds() throws Exception {
		mockMvc.perform(get("/api/test-tenants/" + tenant1.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + user1Token))
				.andExpect(status().isOk());
	}

	@Test
	void userCannotAccessAnotherTenant() throws Exception {
		mockMvc.perform(get("/api/test-tenants/" + tenant1.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + user2Token))
				.andExpect(status().isForbidden());
	}

	@Test
	void crossTenantAccessIsBlocked() throws Exception {
		mockMvc.perform(get("/api/test-tenants/" + tenant1.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + user2Token))
				.andExpect(status().isForbidden());
	}

	@Test
	void roleRestrictionsSucceedForOwner() throws Exception {
		mockMvc.perform(get("/api/test-tenants/" + tenant1.getId() + "/admin")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + user1Token))
				.andExpect(status().isOk());
	}

	@Test
	void roleRestrictionsBlockMemberFromAdmin() throws Exception {
		mockMvc.perform(get("/api/test-tenants/" + tenant2.getId() + "/admin")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + user1Token))
				.andExpect(status().isForbidden());
	}

	@TestConfiguration
	static class TestTenantControllerConfig {

		@RestController
		@RequestMapping("/api/test-tenants")
		static class TestTenantController {

			@GetMapping("/{tenantId}")
			@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
			public String getTenant(@PathVariable UUID tenantId) {
				return "Tenant data";
			}

			@GetMapping("/{tenantId}/admin")
			@PreAuthorize("@tenantSecurity.hasRole(#tenantId, 'ADMIN', 'OWNER')")
			public String getTenantAdmin(@PathVariable UUID tenantId) {
				return "Tenant admin data";
			}
		}
	}
}
