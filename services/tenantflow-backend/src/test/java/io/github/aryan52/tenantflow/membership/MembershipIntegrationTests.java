package io.github.aryan52.tenantflow.membership;

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
import io.github.aryan52.tenantflow.membership.dto.AddMembershipRequest;
import io.github.aryan52.tenantflow.membership.dto.UpdateMembershipRequest;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.TenantRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_membership_api;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.show-sql=false",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-membership-secret-test-membership-secret",
		"tenantflow.jwt.expiration-seconds=3600"
})
@AutoConfigureMockMvc
class MembershipIntegrationTests {

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

	private User ownerUser;
	private User adminUser;
	private User memberUser;
	private User targetUser;
	private Tenant tenant;
	private String ownerToken;
	private String adminToken;
	private String memberToken;

	@BeforeEach
	void setup() {
		membershipRepository.deleteAll();
		tenantRepository.deleteAll();
		userRepository.deleteAll();

		ownerUser = userRepository.save(User.builder().email("owner@example.com").name("Owner").passwordHash("hash").build());
		adminUser = userRepository.save(User.builder().email("admin@example.com").name("Admin").passwordHash("hash").build());
		memberUser = userRepository.save(User.builder().email("member@example.com").name("Member").passwordHash("hash").build());
		targetUser = userRepository.save(User.builder().email("target@example.com").name("Target").passwordHash("hash").build());

		tenant = tenantRepository.save(Tenant.builder().name("My Tenant").slug("my-tenant").build());

		membershipRepository.save(Membership.builder().user(ownerUser).tenant(tenant).role(Role.OWNER).build());
		membershipRepository.save(Membership.builder().user(adminUser).tenant(tenant).role(Role.ADMIN).build());
		membershipRepository.save(Membership.builder().user(memberUser).tenant(tenant).role(Role.MEMBER).build());

		ownerToken = jwtService.generateToken(ownerUser);
		adminToken = jwtService.generateToken(adminUser);
		memberToken = jwtService.generateToken(memberUser);
	}

	@Test
	void getMembershipsSucceedsForMember() throws Exception {
		mockMvc.perform(get("/api/tenants/" + tenant.getId() + "/members")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3));
	}

	@Test
	void addMembershipSucceedsForAdmin() throws Exception {
		AddMembershipRequest request = new AddMembershipRequest(targetUser.getEmail(), Role.MEMBER);

		mockMvc.perform(post("/api/tenants/" + tenant.getId() + "/members")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(targetUser.getEmail()))
				.andExpect(jsonPath("$.role").value("MEMBER"));

		assertThat(membershipRepository.existsByUser_IdAndTenant_Id(targetUser.getId(), tenant.getId())).isTrue();
	}

	@Test
	void addMembershipFailsForMember() throws Exception {
		AddMembershipRequest request = new AddMembershipRequest(targetUser.getEmail(), Role.MEMBER);

		mockMvc.perform(post("/api/tenants/" + tenant.getId() + "/members")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isForbidden());
	}

	@Test
	void addDuplicateMembershipReturns409() throws Exception {
		AddMembershipRequest request = new AddMembershipRequest(memberUser.getEmail(), Role.ADMIN);

		mockMvc.perform(post("/api/tenants/" + tenant.getId() + "/members")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isConflict());
	}

	@Test
	void updateMembershipSucceedsForOwner() throws Exception {
		Membership m = membershipRepository.findByUser_IdAndTenant_Id(memberUser.getId(), tenant.getId()).orElseThrow();
		UpdateMembershipRequest request = new UpdateMembershipRequest(Role.ADMIN);

		mockMvc.perform(put("/api/tenants/" + tenant.getId() + "/members/" + m.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ADMIN"));

		assertThat(membershipRepository.findById(m.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
	}

	@Test
	void removeMembershipSucceedsForAdmin() throws Exception {
		Membership m = membershipRepository.findByUser_IdAndTenant_Id(memberUser.getId(), tenant.getId()).orElseThrow();

		mockMvc.perform(delete("/api/tenants/" + tenant.getId() + "/members/" + m.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isNoContent());

		assertThat(membershipRepository.findById(m.getId())).isEmpty();
	}
}
