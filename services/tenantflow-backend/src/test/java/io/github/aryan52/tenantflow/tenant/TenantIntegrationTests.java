package io.github.aryan52.tenantflow.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.Role;
import io.github.aryan52.tenantflow.entity.Tenant;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.TenantRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.security.JwtService;
import io.github.aryan52.tenantflow.tenant.dto.CreateTenantRequest;
import io.github.aryan52.tenantflow.tenant.dto.TenantResponse;
import io.github.aryan52.tenantflow.tenant.dto.UpdateTenantRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_tenant_api;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.show-sql=false",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-tenant-secret-test-tenant-secret-test-tenant-secret",
		"tenantflow.jwt.expiration-seconds=3600"
})
@AutoConfigureMockMvc
class TenantIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

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
	private String token1;
	private String token2;

	@BeforeEach
	void setup() {
		membershipRepository.deleteAll();
		tenantRepository.deleteAll();
		userRepository.deleteAll();

		user1 = userRepository.save(User.builder().email("user1@example.com").name("U1").passwordHash("hash").build());
		user2 = userRepository.save(User.builder().email("user2@example.com").name("U2").passwordHash("hash").build());

		token1 = jwtService.generateToken(user1);
		token2 = jwtService.generateToken(user2);
	}

	@Test
	void createTenantSuccessfully() throws Exception {
		CreateTenantRequest request = new CreateTenantRequest("My Tenant", "my-tenant");

		MvcResult result = mockMvc.perform(post("/api/tenants")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("My Tenant"))
				.andExpect(jsonPath("$.slug").value("my-tenant"))
				.andExpect(jsonPath("$.myRole").value("OWNER"))
				.andReturn();

		TenantResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), TenantResponse.class);
		
		Membership membership = membershipRepository.findByUser_IdAndTenant_Id(user1.getId(), response.id()).orElseThrow();
		assertThat(membership.getRole()).isEqualTo(Role.OWNER);
	}

	@Test
	void rejectDuplicateTenantSlug() throws Exception {
		tenantRepository.save(Tenant.builder().name("T1").slug("duplicate-slug").build());

		CreateTenantRequest request = new CreateTenantRequest("Another", "duplicate-slug");

		mockMvc.perform(post("/api/tenants")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Tenant with slug 'duplicate-slug' already exists"));
	}

	@Test
	void listAccessibleTenants() throws Exception {
		Tenant t1 = tenantRepository.save(Tenant.builder().name("T1").slug("t-1").build());
		Tenant t2 = tenantRepository.save(Tenant.builder().name("T2").slug("t-2").build());
		// user1 is in t1 and t2
		membershipRepository.save(Membership.builder().user(user1).tenant(t1).role(Role.OWNER).build());
		membershipRepository.save(Membership.builder().user(user1).tenant(t2).role(Role.MEMBER).build());
		
		// user2 is only in t2
		membershipRepository.save(Membership.builder().user(user2).tenant(t2).role(Role.ADMIN).build());

		mockMvc.perform(get("/api/tenants")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));

		mockMvc.perform(get("/api/tenants")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token2))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].slug").value("t-2"))
				.andExpect(jsonPath("$[0].myRole").value("ADMIN"));
	}

	@Test
	void getSpecificTenant() throws Exception {
		Tenant t1 = tenantRepository.save(Tenant.builder().name("T1").slug("t-1").build());
		membershipRepository.save(Membership.builder().user(user1).tenant(t1).role(Role.MEMBER).build());

		mockMvc.perform(get("/api/tenants/" + t1.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slug").value("t-1"));
	}

	@Test
	void getTenantFailsForNonMember() throws Exception {
		Tenant t1 = tenantRepository.save(Tenant.builder().name("T1").slug("t-1").build());

		mockMvc.perform(get("/api/tenants/" + t1.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
				.andExpect(status().isForbidden());
	}

	@Test
	void updateTenantSucceedsForAdmin() throws Exception {
		Tenant t1 = tenantRepository.save(Tenant.builder().name("Old Name").slug("t-1").build());
		membershipRepository.save(Membership.builder().user(user1).tenant(t1).role(Role.ADMIN).build());

		UpdateTenantRequest request = new UpdateTenantRequest("New Name");

		mockMvc.perform(put("/api/tenants/" + t1.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("New Name"));

		assertThat(tenantRepository.findById(t1.getId()).orElseThrow().getName()).isEqualTo("New Name");
	}

	@Test
	void updateTenantFailsForMember() throws Exception {
		Tenant t1 = tenantRepository.save(Tenant.builder().name("Old Name").slug("t-1").build());
		membershipRepository.save(Membership.builder().user(user1).tenant(t1).role(Role.MEMBER).build());

		UpdateTenantRequest request = new UpdateTenantRequest("New Name");

		mockMvc.perform(put("/api/tenants/" + t1.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isForbidden());
	}

	@Test
	void deleteTenantSucceedsForOwner() throws Exception {
		Tenant t1 = tenantRepository.save(Tenant.builder().name("T1").slug("t-1").build());
		membershipRepository.save(Membership.builder().user(user1).tenant(t1).role(Role.OWNER).build());

		mockMvc.perform(delete("/api/tenants/" + t1.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
				.andExpect(status().isNoContent());

		assertThat(tenantRepository.findById(t1.getId())).isEmpty();
	}

	@Test
	void deleteTenantFailsForAdmin() throws Exception {
		Tenant t1 = tenantRepository.save(Tenant.builder().name("T1").slug("t-1").build());
		membershipRepository.save(Membership.builder().user(user1).tenant(t1).role(Role.ADMIN).build());

		mockMvc.perform(delete("/api/tenants/" + t1.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
				.andExpect(status().isForbidden());
	}
}
