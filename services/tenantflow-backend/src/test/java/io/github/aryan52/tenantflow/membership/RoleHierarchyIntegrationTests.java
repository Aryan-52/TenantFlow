package io.github.aryan52.tenantflow.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.aryan52.tenantflow.audit.AuditAction;
import io.github.aryan52.tenantflow.audit.AuditEvent;
import io.github.aryan52.tenantflow.audit.AuditEventRepository;
import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.Role;
import io.github.aryan52.tenantflow.entity.Tenant;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.TenantRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.security.JwtService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The workspace role hierarchy (see {@link Role}): nobody may grant a role above their own,
 * act on a member ranked above them, or change their own role; the last OWNER is protected.
 * Every denied attempt is audited even though the request itself is rolled back.
 */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_role_hierarchy;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-role-hierarchy-secret-test-role-hierarchy-secret",
		"tenantflow.jwt.expiration-seconds=3600"
})
@AutoConfigureMockMvc
class RoleHierarchyIntegrationTests {

	@Autowired private MockMvc mockMvc;
	@Autowired private UserRepository userRepository;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private MembershipRepository membershipRepository;
	@Autowired private AuditEventRepository auditEventRepository;
	@Autowired private JwtService jwtService;

	private Tenant tenant;
	private Tenant otherTenant;
	private Membership ownerM;
	private Membership adminM;
	private Membership admin2M;
	private Membership memberM;
	private Membership foreignOwnerM;
	private String ownerToken;
	private String adminToken;
	private String memberToken;
	private String foreignOwnerToken;

	@BeforeEach
	void setup() {
		auditEventRepository.deleteAll();
		membershipRepository.deleteAll();
		tenantRepository.deleteAll();
		userRepository.deleteAll();

		User owner = user("owner@example.com");
		User admin = user("admin@example.com");
		User admin2 = user("admin2@example.com");
		User member = user("member@example.com");
		User foreignOwner = user("foreign@example.com");
		user("newcomer@example.com");

		tenant = tenantRepository.save(Tenant.builder().name("T").slug("t").build());
		otherTenant = tenantRepository.save(Tenant.builder().name("Other").slug("other").build());
		ownerM = membership(owner, tenant, Role.OWNER);
		adminM = membership(admin, tenant, Role.ADMIN);
		admin2M = membership(admin2, tenant, Role.ADMIN);
		memberM = membership(member, tenant, Role.MEMBER);
		foreignOwnerM = membership(foreignOwner, otherTenant, Role.OWNER);

		ownerToken = jwtService.generateToken(owner);
		adminToken = jwtService.generateToken(admin);
		memberToken = jwtService.generateToken(member);
		foreignOwnerToken = jwtService.generateToken(foreignOwner);
	}

	private User user(String email) {
		return userRepository.save(User.builder().email(email).name(email.substring(0, email.indexOf('@'))).passwordHash("hash").build());
	}

	private Membership membership(User u, Tenant t, Role role) {
		return membershipRepository.save(Membership.builder().user(u).tenant(t).role(role).build());
	}

	private String members(Tenant t) {
		return "/api/tenants/" + t.getId() + "/members";
	}

	private ResultActions changeRole(String token, Membership target, Role role) throws Exception {
		return mockMvc.perform(put(members(tenant) + "/" + target.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"role\":\"" + role + "\"}"));
	}

	private ResultActions add(String token, String email, Role role) throws Exception {
		return mockMvc.perform(post(members(tenant))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}"));
	}

	private Role roleOf(Membership m) {
		return membershipRepository.findById(m.getId()).orElseThrow().getRole();
	}

	private List<AuditEvent> denials() {
		return auditEventRepository.findByAction(AuditAction.MEMBER_CHANGE_DENIED);
	}

	// ---------------------------------------------------------------- ADMIN escalation

	@Test
	void adminCannotPromoteThemselvesToOwner() throws Exception {
		changeRole(adminToken, adminM, Role.OWNER)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("You cannot change your own role"));
		assertThat(roleOf(adminM)).isEqualTo(Role.ADMIN);
		assertThat(denials()).hasSize(1);
		assertThat(denials().get(0).getOutcome()).isEqualTo(AuditEvent.Outcome.DENIED);
	}

	@Test
	void adminCannotPromoteSomeoneElseToOwner() throws Exception {
		changeRole(adminToken, memberM, Role.OWNER).andExpect(status().isForbidden());
		changeRole(adminToken, admin2M, Role.OWNER).andExpect(status().isForbidden());
		assertThat(roleOf(memberM)).isEqualTo(Role.MEMBER);
		assertThat(roleOf(admin2M)).isEqualTo(Role.ADMIN);
		assertThat(denials()).hasSize(2);
	}

	@Test
	void adminCannotAddANewOwner() throws Exception {
		add(adminToken, "newcomer@example.com", Role.OWNER).andExpect(status().isForbidden());
		assertThat(membershipRepository.countByTenant_Id(tenant.getId())).isEqualTo(4);
		assertThat(denials()).hasSize(1);
	}

	@Test
	void adminCannotDemoteOrRemoveTheOwner() throws Exception {
		changeRole(adminToken, ownerM, Role.MEMBER).andExpect(status().isForbidden());
		mockMvc.perform(delete(members(tenant) + "/" + ownerM.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isForbidden());
		assertThat(roleOf(ownerM)).isEqualTo(Role.OWNER);
	}

	@Test
	void adminCanManageMembersAndAdminsUpToTheirOwnRank() throws Exception {
		add(adminToken, "newcomer@example.com", Role.ADMIN).andExpect(status().isCreated());
		changeRole(adminToken, memberM, Role.ADMIN).andExpect(status().isOk());
		changeRole(adminToken, admin2M, Role.MEMBER).andExpect(status().isOk());
		assertThat(roleOf(memberM)).isEqualTo(Role.ADMIN);
		assertThat(roleOf(admin2M)).isEqualTo(Role.MEMBER);
		assertThat(auditEventRepository.findByAction(AuditAction.MEMBER_ROLE_CHANGED)).hasSize(2);
		assertThat(auditEventRepository.findByAction(AuditAction.MEMBER_ADDED)).hasSize(1);
	}

	// ---------------------------------------------------------------- MEMBER and OWNER

	@Test
	void memberCannotChangeAnyRoleIncludingTheirOwn() throws Exception {
		changeRole(memberToken, memberM, Role.ADMIN).andExpect(status().isForbidden());
		add(memberToken, "newcomer@example.com", Role.MEMBER).andExpect(status().isForbidden());
		assertThat(roleOf(memberM)).isEqualTo(Role.MEMBER);
	}

	@Test
	void ownerCanAppointAnotherOwnerWhoCanThenManageTheFirst() throws Exception {
		changeRole(ownerToken, adminM, Role.OWNER).andExpect(status().isOk());
		changeRole(adminToken, ownerM, Role.ADMIN).andExpect(status().isOk());
		assertThat(roleOf(ownerM)).isEqualTo(Role.ADMIN);
		assertThat(roleOf(adminM)).isEqualTo(Role.OWNER);
	}

	@Test
	void ownerCannotChangeOwnRoleAndTheLastOwnerCannotLeave() throws Exception {
		changeRole(ownerToken, ownerM, Role.ADMIN).andExpect(status().isForbidden());
		mockMvc.perform(delete(members(tenant) + "/" + ownerM.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Cannot remove the last owner"));
		assertThat(roleOf(ownerM)).isEqualTo(Role.OWNER);
	}

	@Test
	void membershipsOfAnotherWorkspaceCannotBeTouched() throws Exception {
		// Owner of tenant 1 addressing tenant 2's membership through tenant 1's URL.
		changeRole(ownerToken, foreignOwnerM, Role.MEMBER).andExpect(status().isNotFound());
		// Owner of tenant 2 addressing tenant 1 directly.
		changeRole(foreignOwnerToken, memberM, Role.ADMIN).andExpect(status().isForbidden());
		assertThat(roleOf(foreignOwnerM)).isEqualTo(Role.OWNER);
		assertThat(roleOf(memberM)).isEqualTo(Role.MEMBER);
	}

	// ---------------------------------------------------------------- audit log access

	@Test
	void auditLogIsVisibleToAdminsOnlyAndScopedToTheWorkspace() throws Exception {
		changeRole(adminToken, adminM, Role.OWNER); // denied -> audited for tenant 1
		changeRole(ownerToken, admin2M, Role.MEMBER).andExpect(status().isOk());
		// Something audited in tenant 2 that must never show up in tenant 1.
		mockMvc.perform(post(members(otherTenant))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + foreignOwnerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"newcomer@example.com\",\"role\":\"MEMBER\"}"))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/tenants/" + tenant.getId() + "/audit-events")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.items[0].action").value("MEMBER_ROLE_CHANGED"))
				.andExpect(jsonPath("$.items[1].action").value("MEMBER_CHANGE_DENIED"))
				.andExpect(jsonPath("$.items[1].outcome").value("DENIED"))
				.andExpect(jsonPath("$.items[0].ipAddress").doesNotExist());

		mockMvc.perform(get("/api/tenants/" + tenant.getId() + "/audit-events")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/tenants/" + tenant.getId() + "/audit-events")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + foreignOwnerToken))
				.andExpect(status().isForbidden());
	}

	@Test
	void auditLogPageSizeIsCapped() throws Exception {
		mockMvc.perform(get("/api/tenants/" + tenant.getId() + "/audit-events?size=1000")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("size must be between 1 and 100"));
		mockMvc.perform(get("/api/tenants/" + tenant.getId() + "/audit-events?page=-1")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
				.andExpect(status().isBadRequest());
	}
}
