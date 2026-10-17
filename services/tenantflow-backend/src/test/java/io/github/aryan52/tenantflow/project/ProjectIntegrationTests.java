package io.github.aryan52.tenantflow.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.Project;
import io.github.aryan52.tenantflow.entity.ProjectStatus;
import io.github.aryan52.tenantflow.entity.Task;
import io.github.aryan52.tenantflow.entity.TaskPriority;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import io.github.aryan52.tenantflow.entity.Role;
import io.github.aryan52.tenantflow.entity.Tenant;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import io.github.aryan52.tenantflow.repository.TaskRepository;
import io.github.aryan52.tenantflow.repository.TenantRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.security.JwtService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_project_api;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
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
class ProjectIntegrationTests {

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
	private ProjectRepository projectRepository;

	@Autowired
	private TaskRepository taskRepository;

	@Autowired
	private JwtService jwtService;

	private User adminUser;
	private User memberUser;
	private User otherUser;
	private Tenant tenant;
	private Tenant otherTenant;
	private String adminToken;
	private String memberToken;
	private String otherToken;

	private String body(Object... keyValues) throws Exception {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			map.put((String) keyValues[i], keyValues[i + 1]);
		}
		return objectMapper.writeValueAsString(map);
	}

	private String projectsUrl(Tenant t) {
		return "/api/tenants/" + t.getId() + "/projects";
	}

	@BeforeEach
	void setup() {
		taskRepository.deleteAll();
		projectRepository.deleteAll();
		membershipRepository.deleteAll();
		tenantRepository.deleteAll();
		userRepository.deleteAll();

		adminUser = userRepository.save(User.builder().email("admin@example.com").name("A").passwordHash("hash").build());
		memberUser = userRepository.save(User.builder().email("member@example.com").name("M").passwordHash("hash").build());
		otherUser = userRepository.save(User.builder().email("other@example.com").name("O").passwordHash("hash").build());

		adminToken = jwtService.generateToken(adminUser);
		memberToken = jwtService.generateToken(memberUser);
		otherToken = jwtService.generateToken(otherUser);

		tenant = tenantRepository.save(Tenant.builder().name("T1").slug("t-1").build());
		otherTenant = tenantRepository.save(Tenant.builder().name("T2").slug("t-2").build());

		membershipRepository.save(Membership.builder().user(adminUser).tenant(tenant).role(Role.ADMIN).build());
		membershipRepository.save(Membership.builder().user(memberUser).tenant(tenant).role(Role.MEMBER).build());
		membershipRepository.save(Membership.builder().user(otherUser).tenant(otherTenant).role(Role.OWNER).build());
	}

	@Test
	void adminCanCreateProject() throws Exception {
		String request = body("name", "My Project", "description", "Description");

		mockMvc.perform(post("/api/tenants/" + tenant.getId() + "/projects")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("My Project"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.createdById").value(adminUser.getId().toString()))
				.andExpect(jsonPath("$.createdByName").value("A"))
				.andExpect(jsonPath("$.taskCount").value(0));

		assertThat(projectRepository.countByTenant_Id(tenant.getId())).isEqualTo(1);
	}

	@Test
	void memberCannotCreateProject() throws Exception {
		String request = body("name", "My Project", "description", "Description");

		mockMvc.perform(post("/api/tenants/" + tenant.getId() + "/projects")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isForbidden());
	}

	@Test
	void crossTenantAccessBlocked() throws Exception {
		String request = body("name", "My Project", "description", "Description");

		mockMvc.perform(post("/api/tenants/" + tenant.getId() + "/projects")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminCanUpdateProject() throws Exception {
		Project project = projectRepository.save(Project.builder().tenant(tenant).name("Old").build());

		String request = body("name", "New", "description", "Desc", "status", "ON_HOLD");

		mockMvc.perform(put("/api/tenants/" + tenant.getId() + "/projects/" + project.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("New"))
				.andExpect(jsonPath("$.status").value("ON_HOLD"));
	}

	@Test
	void memberCanViewProjects() throws Exception {
		Project project = projectRepository.save(Project.builder().tenant(tenant).name("P1").build());

		mockMvc.perform(get("/api/tenants/" + tenant.getId() + "/projects")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("P1"));

		mockMvc.perform(get("/api/tenants/" + tenant.getId() + "/projects/" + project.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("P1"));
	}

	@Test
	void adminCanDeleteProject() throws Exception {
		Project project = projectRepository.save(Project.builder().tenant(tenant).name("P1").build());

		mockMvc.perform(delete("/api/tenants/" + tenant.getId() + "/projects/" + project.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isNoContent());

		assertThat(projectRepository.findById(project.getId())).isEmpty();
	}

	@Test
	void createProjectWithExplicitStatus() throws Exception {
		String request = body("name", "Paused", "status", "ON_HOLD");

		mockMvc.perform(post("/api/tenants/" + tenant.getId() + "/projects")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("ON_HOLD"));
	}

	@Test
	void updateWithoutStatusKeepsCurrentStatus() throws Exception {
		Project project = projectRepository.save(Project.builder().tenant(tenant).name("Old").status(ProjectStatus.COMPLETED).build());

		mockMvc.perform(put("/api/tenants/" + tenant.getId() + "/projects/" + project.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Renamed\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Renamed"))
				.andExpect(jsonPath("$.status").value("COMPLETED"));
	}

	@Test
	void createProjectValidation() throws Exception {
		mockMvc.perform(post("/api/tenants/" + tenant.getId() + "/projects")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.name").exists());

		mockMvc.perform(post("/api/tenants/" + tenant.getId() + "/projects")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Ok\",\"status\":\"BOGUS\"}"))
				.andExpect(status().isBadRequest());

		assertThat(projectRepository.countByTenant_Id(tenant.getId())).isZero();
	}

	@Test
	void memberCannotUpdateOrDeleteProject() throws Exception {
		Project project = projectRepository.save(Project.builder().tenant(tenant).name("P1").build());

		mockMvc.perform(put("/api/tenants/" + tenant.getId() + "/projects/" + project.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("name", "X")))
				.andExpect(status().isForbidden());

		mockMvc.perform(delete("/api/tenants/" + tenant.getId() + "/projects/" + project.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isForbidden());

		assertThat(projectRepository.findById(project.getId())).isPresent();
	}

	@Test
	void listIncludesTaskCounts() throws Exception {
		Project project = projectRepository.save(Project.builder().tenant(tenant).name("P1").build());
		taskRepository.save(Task.builder().project(project).title("a").status(TaskStatus.TODO).priority(TaskPriority.LOW).build());
		taskRepository.save(Task.builder().project(project).title("b").status(TaskStatus.DONE).priority(TaskPriority.LOW).build());

		mockMvc.perform(get("/api/tenants/" + tenant.getId() + "/projects")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].taskCount").value(2))
				.andExpect(jsonPath("$[0].completedTaskCount").value(1));

		mockMvc.perform(get("/api/tenants/" + tenant.getId() + "/projects/" + project.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.taskCount").value(2))
				.andExpect(jsonPath("$.completedTaskCount").value(1));
	}

	@Test
	void deletingProjectRemovesItsTasks() throws Exception {
		Project project = projectRepository.save(Project.builder().tenant(tenant).name("P1").build());
		Task task = taskRepository.save(Task.builder().project(project).title("a").status(TaskStatus.TODO).priority(TaskPriority.LOW).build());

		mockMvc.perform(delete("/api/tenants/" + tenant.getId() + "/projects/" + project.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isNoContent());

		assertThat(projectRepository.findById(project.getId())).isEmpty();
		assertThat(taskRepository.findById(task.getId())).isEmpty();
	}

	@Test
	void projectOfAnotherTenantIsNotReachableThroughOwnTenant() throws Exception {
		Project foreign = projectRepository.save(Project.builder().tenant(otherTenant).name("Secret").build());

		// Admin of tenant 1 addressing tenant 2's project through tenant 1's path.
		mockMvc.perform(get("/api/tenants/" + tenant.getId() + "/projects/" + foreign.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isNotFound());

		mockMvc.perform(put("/api/tenants/" + tenant.getId() + "/projects/" + foreign.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("name", "Hijacked")))
				.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/tenants/" + tenant.getId() + "/projects/" + foreign.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isNotFound());

		// Admin of tenant 1 addressing tenant 2 directly.
		mockMvc.perform(get("/api/tenants/" + otherTenant.getId() + "/projects")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isForbidden());

		assertThat(projectRepository.findById(foreign.getId())).get()
				.extracting(Project::getName).isEqualTo("Secret");
	}

	@Test
	void createWithoutOwnerMakesCreatorTheOwner() throws Exception {
		mockMvc.perform(post(projectsUrl(tenant))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("name", "Mine")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ownerId").value(adminUser.getId().toString()))
				.andExpect(jsonPath("$.ownerName").value("A"));
	}

	@Test
	void createWithMemberOwnerOrUnassigned() throws Exception {
		mockMvc.perform(post(projectsUrl(tenant))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("name", "Led by member", "ownerId", memberUser.getId().toString())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ownerId").value(memberUser.getId().toString()))
				.andExpect(jsonPath("$.ownerName").value("M"))
				// The creator is still recorded separately from the owner.
				.andExpect(jsonPath("$.createdById").value(adminUser.getId().toString()));

		mockMvc.perform(post(projectsUrl(tenant))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("name", "Nobody yet", "ownerId", null)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ownerId").doesNotExist())
				.andExpect(jsonPath("$.ownerName").doesNotExist());
	}

	@Test
	void ownerFromAnotherWorkspaceIsRejected() throws Exception {
		mockMvc.perform(post(projectsUrl(tenant))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("name", "Sneaky", "ownerId", otherUser.getId().toString())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Project owner must be a member of this workspace"));
		assertThat(projectRepository.countByTenant_Id(tenant.getId())).isZero();

		Project project = projectRepository.save(Project.builder().tenant(tenant).name("P").owner(adminUser).build());
		mockMvc.perform(put(projectsUrl(tenant) + "/" + project.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("name", "P", "ownerId", otherUser.getId().toString())))
				.andExpect(status().isBadRequest());

		// A random, non-existent user id is rejected the same way.
		mockMvc.perform(put(projectsUrl(tenant) + "/" + project.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("name", "P", "ownerId", java.util.UUID.randomUUID().toString())))
				.andExpect(status().isBadRequest());

		assertThat(projectRepository.findById(project.getId()).orElseThrow().getOwner().getId()).isEqualTo(adminUser.getId());
	}

	@Test
	void updateOwnerReassignsKeepsWhenOmittedAndUnassignsOnNull() throws Exception {
		Project project = projectRepository.save(Project.builder().tenant(tenant).name("P").owner(adminUser).build());
		String url = projectsUrl(tenant) + "/" + project.getId();

		mockMvc.perform(put(url)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("name", "P", "ownerId", memberUser.getId().toString())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ownerName").value("M"));

		// Omitted ownerId: an edit of other fields must not touch the owner.
		mockMvc.perform(put(url)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("name", "Renamed", "status", "ON_HOLD")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Renamed"))
				.andExpect(jsonPath("$.ownerId").value(memberUser.getId().toString()));

		// Explicit null: unassign.
		mockMvc.perform(put(url)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("name", "Renamed", "ownerId", null)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ownerId").doesNotExist());

		assertThat(projectRepository.findById(project.getId()).orElseThrow().getOwner()).isNull();
	}

	@Test
	void presenceFlagsCannotBeSetFromJson() throws Exception {
		// "Was ownerId sent?" is decided by the server from the JSON itself. A client sending the
		// internal flag must not unassign the owner (or clear dates) without sending the field.
		Project project = projectRepository.save(Project.builder().tenant(tenant).name("P").owner(memberUser)
				.startDate(java.time.LocalDate.of(2026, 1, 5)).build());
		mockMvc.perform(put(projectsUrl(tenant) + "/" + project.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"P\",\"ownerIdPresent\":true,\"startDatePresent\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ownerId").value(memberUser.getId().toString()))
				.andExpect(jsonPath("$.startDate").value("2026-01-05"));
	}

	@Test
	void creatorIsRecordedOnceAndIsReadOnly() throws Exception {
		String created = mockMvc.perform(post(projectsUrl(tenant))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("name", "Mine")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String id = objectMapper.readTree(created).get("id").asText();

		// Another admin edits it (and tries to claim it): the creator does not change.
		User secondAdmin = userRepository.save(User.builder().email("admin2@example.com").name("B").passwordHash("hash").build());
		membershipRepository.save(Membership.builder().user(secondAdmin).tenant(tenant).role(Role.ADMIN).build());
		mockMvc.perform(put(projectsUrl(tenant) + "/" + id)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(secondAdmin))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("name", "Renamed", "createdById", secondAdmin.getId().toString())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.createdById").value(adminUser.getId().toString()));

		// Persisted, and returned by the detail endpoint the Overview uses.
		mockMvc.perform(get(projectsUrl(tenant) + "/" + id)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.createdById").value(adminUser.getId().toString()))
				.andExpect(jsonPath("$.createdByName").value("A"));
	}

	@Test
	void projectWithoutRecordedCreatorIsNeverAttributedToSomeone() throws Exception {
		// Projects created before creator tracking (V6) have no creator; an edit must not invent one.
		Project legacy = projectRepository.save(Project.builder().tenant(tenant).name("Legacy").owner(adminUser).build());
		mockMvc.perform(put(projectsUrl(tenant) + "/" + legacy.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("name", "Legacy 2")))
				.andExpect(status().isOk());
		mockMvc.perform(get(projectsUrl(tenant) + "/" + legacy.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ownerName").value("A"))
				.andExpect(jsonPath("$.createdById").doesNotExist())
				.andExpect(jsonPath("$.createdByName").doesNotExist());
		assertThat(projectRepository.findById(legacy.getId()).orElseThrow().getCreatedBy()).isNull();
	}

	@Test
	void ownerIsReturnedInListAndDetail() throws Exception {
		Project project = projectRepository.save(Project.builder().tenant(tenant).name("P").owner(memberUser).createdBy(adminUser).build());

		mockMvc.perform(get(projectsUrl(tenant))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].ownerName").value("M"))
				.andExpect(jsonPath("$[0].createdByName").value("A"));

		mockMvc.perform(get(projectsUrl(tenant) + "/" + project.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ownerId").value(memberUser.getId().toString()))
				.andExpect(jsonPath("$.ownerName").value("M"));
	}

	@Test
	void removingAMemberUnassignsTheirProjectsWithoutDeletingThem() throws Exception {
		Project owned = projectRepository.save(Project.builder().tenant(tenant).name("Owned").owner(memberUser).build());
		// The same user owning a project in another workspace must not be affected.
		membershipRepository.save(Membership.builder().user(memberUser).tenant(otherTenant).role(Role.MEMBER).build());
		Project elsewhere = projectRepository.save(Project.builder().tenant(otherTenant).name("Elsewhere").owner(memberUser).build());
		Membership membership = membershipRepository.findByUser_IdAndTenant_Id(memberUser.getId(), tenant.getId()).orElseThrow();

		mockMvc.perform(delete("/api/tenants/" + tenant.getId() + "/members/" + membership.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isNoContent());

		Project after = projectRepository.findById(owned.getId()).orElseThrow();
		assertThat(after.getOwner()).isNull();
		assertThat(after.getName()).isEqualTo("Owned");
		assertThat(projectRepository.findById(elsewhere.getId()).orElseThrow().getOwner().getId()).isEqualTo(memberUser.getId());

		mockMvc.perform(get(projectsUrl(tenant) + "/" + owned.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ownerId").doesNotExist());
	}
}
