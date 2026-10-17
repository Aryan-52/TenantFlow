package io.github.aryan52.tenantflow.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.aryan52.tenantflow.activity.ActivityEventRepository;
import io.github.aryan52.tenantflow.audit.AuditEventRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_notifications;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-notification-secret-test-notification-secret-tst"
})
@AutoConfigureMockMvc
class NotificationActivityIntegrationTests {

	@Autowired private MockMvc mockMvc;
	@Autowired private UserRepository userRepository;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private MembershipRepository membershipRepository;
	@Autowired private ProjectRepository projectRepository;
	@Autowired private TaskRepository taskRepository;
	@Autowired private NotificationRepository notificationRepository;
	@Autowired private ActivityEventRepository activityEventRepository;
	@Autowired private AuditEventRepository auditEventRepository;
	@Autowired private JwtService jwtService;

	private User alice; // admin, the actor
	private User bob;   // member, the assignee
	private User carol; // member of another workspace only
	private Tenant tenant;
	private Tenant otherTenant;
	private Project project;
	private Membership bobMembership;
	private String aliceToken;
	private String bobToken;
	private String carolToken;

	@BeforeEach
	void setup() {
		notificationRepository.deleteAll();
		activityEventRepository.deleteAll();
		auditEventRepository.deleteAll();
		taskRepository.deleteAll();
		projectRepository.deleteAll();
		membershipRepository.deleteAll();
		tenantRepository.deleteAll();
		userRepository.deleteAll();

		alice = userRepository.save(User.builder().email("alice@example.com").name("Alice").passwordHash("h").build());
		bob = userRepository.save(User.builder().email("bob@example.com").name("Bob").passwordHash("h").build());
		carol = userRepository.save(User.builder().email("carol@example.com").name("Carol").passwordHash("h").build());
		tenant = tenantRepository.save(Tenant.builder().name("Acme").slug("acme").build());
		otherTenant = tenantRepository.save(Tenant.builder().name("Other").slug("other").build());
		membershipRepository.save(Membership.builder().user(alice).tenant(tenant).role(Role.OWNER).build());
		bobMembership = membershipRepository.save(Membership.builder().user(bob).tenant(tenant).role(Role.MEMBER).build());
		membershipRepository.save(Membership.builder().user(carol).tenant(otherTenant).role(Role.OWNER).build());
		project = projectRepository.save(Project.builder().tenant(tenant).name("Apollo").build());
		aliceToken = jwtService.generateToken(alice);
		bobToken = jwtService.generateToken(bob);
		carolToken = jwtService.generateToken(carol);
	}

	private String tasksUrl() {
		return "/api/tenants/" + tenant.getId() + "/projects/" + project.getId() + "/tasks";
	}

	private ResultActions as(String token, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req) throws Exception {
		return mockMvc.perform(req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private String createTask(String json) throws Exception {
		String body = as(aliceToken, post(tasksUrl()).contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private long unread(String token) throws Exception {
		String body = as(token, get("/api/notifications/unread-count")).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.count")).longValue();
	}

	@Test
	void assigningATaskNotifiesTheAssigneeOnceAndNeverTheActor() throws Exception {
		String taskId = createTask("{\"title\":\"Ship\",\"status\":\"TODO\",\"priority\":\"HIGH\",\"assigneeId\":\"" + bob.getId() + "\"}");

		assertThat(unread(bobToken)).isEqualTo(1);
		assertThat(unread(aliceToken)).isZero();
		as(bobToken, get("/api/notifications"))
				.andExpect(jsonPath("$.items[0].type").value("TASK_ASSIGNED"))
				.andExpect(jsonPath("$.items[0].title").value("You were assigned: Ship"))
				.andExpect(jsonPath("$.items[0].link").value(tasksUrl().replace("/api", "") + "?task=" + taskId))
				.andExpect(jsonPath("$.items[0].read").value(false));

		// Re-sending the identical update (a retry) changes nothing and notifies nobody.
		String same = "{\"title\":\"Ship\",\"status\":\"TODO\",\"priority\":\"HIGH\",\"assigneeId\":\"" + bob.getId() + "\"}";
		as(aliceToken, put(tasksUrl() + "/" + taskId).contentType(MediaType.APPLICATION_JSON).content(same)).andExpect(status().isOk());
		as(aliceToken, put(tasksUrl() + "/" + taskId).contentType(MediaType.APPLICATION_JSON).content(same)).andExpect(status().isOk());
		assertThat(unread(bobToken)).isEqualTo(1);
		assertThat(activityEventRepository.count()).isEqualTo(1);
	}

	@Test
	void statusAndDueDateChangesNotifyTheAssigneeAndRetriesAreDeduplicated() throws Exception {
		String taskId = createTask("{\"title\":\"Ship\",\"status\":\"TODO\",\"priority\":\"LOW\",\"assigneeId\":\"" + bob.getId() + "\"}");
		as(bobToken, post("/api/notifications/read-all")).andExpect(jsonPath("$.updated").value(1));

		// The same Kanban drop sent twice (network retry).
		for (int i = 0; i < 2; i++) {
			as(aliceToken, patch(tasksUrl() + "/" + taskId + "/status").contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"IN_PROGRESS\"}")).andExpect(status().isOk());
		}
		assertThat(unread(bobToken)).isEqualTo(1);

		as(aliceToken, put(tasksUrl() + "/" + taskId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Ship\",\"status\":\"IN_PROGRESS\",\"priority\":\"LOW\",\"assigneeId\":\"" + bob.getId()
						+ "\",\"dueDate\":\"2026-12-01\"}")).andExpect(status().isOk());
		assertThat(unread(bobToken)).isEqualTo(2);
		as(bobToken, get("/api/notifications?unreadOnly=true"))
				.andExpect(jsonPath("$.items[0].type").value("TASK_DUE_DATE_CHANGED"))
				.andExpect(jsonPath("$.items[1].type").value("TASK_STATUS_CHANGED"));

		// The assignee changing their own task notifies nobody.
		as(bobToken, patch(tasksUrl() + "/" + taskId + "/status").contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"DONE\"}")).andExpect(status().isOk());
		assertThat(unread(bobToken)).isEqualTo(2);
	}

	@Test
	void usersCanOnlySeeAndChangeTheirOwnNotifications() throws Exception {
		createTask("{\"title\":\"Ship\",\"status\":\"TODO\",\"priority\":\"LOW\",\"assigneeId\":\"" + bob.getId() + "\"}");
		String id = notificationRepository.findAll().get(0).getId().toString();

		as(carolToken, get("/api/notifications")).andExpect(jsonPath("$.totalElements").value(0));
		as(carolToken, post("/api/notifications/" + id + "/read")).andExpect(status().isNotFound());
		as(aliceToken, post("/api/notifications/" + id + "/read")).andExpect(status().isNotFound());
		as(carolToken, post("/api/notifications/read-all")).andExpect(jsonPath("$.updated").value(0));
		assertThat(unread(bobToken)).isEqualTo(1);

		as(bobToken, post("/api/notifications/" + id + "/read")).andExpect(jsonPath("$.read").value(true));
		as(bobToken, post("/api/notifications/" + id + "/read")).andExpect(status().isOk()); // idempotent
		assertThat(unread(bobToken)).isZero();
	}

	@Test
	void leavingAWorkspaceHidesItsNotificationsButShowsTheRemovalNotice() throws Exception {
		createTask("{\"title\":\"Ship\",\"status\":\"TODO\",\"priority\":\"LOW\",\"assigneeId\":\"" + bob.getId() + "\"}");
		assertThat(unread(bobToken)).isEqualTo(1);

		as(aliceToken, delete("/api/tenants/" + tenant.getId() + "/members/" + bobMembership.getId())).andExpect(status().isNoContent());

		as(bobToken, get("/api/notifications"))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items[0].type").value("MEMBER_REMOVED"))
				.andExpect(jsonPath("$.items[0].title").value("You were removed from Acme"))
				.andExpect(jsonPath("$.items[0].tenantId").doesNotExist());
		assertThat(unread(bobToken)).isEqualTo(1);
	}

	@Test
	void membershipAndOwnershipChangesNotifyTheAffectedPerson() throws Exception {
		as(aliceToken, put("/api/tenants/" + tenant.getId() + "/members/" + bobMembership.getId())
				.contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}")).andExpect(status().isOk());
		as(aliceToken, put("/api/tenants/" + tenant.getId() + "/projects/" + project.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Apollo\",\"ownerId\":\"" + bob.getId() + "\"}")).andExpect(status().isOk());

		as(bobToken, get("/api/notifications"))
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.items[0].type").value("PROJECT_OWNER_ASSIGNED"))
				.andExpect(jsonPath("$.items[1].title").value("Your role in Acme is now Admin"));
	}

	@Test
	void activityIsVisibleToMembersOnlyAndProjectFilterIsTenantScoped() throws Exception {
		String taskId = createTask("{\"title\":\"Ship\",\"status\":\"TODO\",\"priority\":\"LOW\"}");
		as(bobToken, patch(tasksUrl() + "/" + taskId + "/status").contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"DONE\"}")).andExpect(status().isOk());

		as(bobToken, get("/api/tenants/" + tenant.getId() + "/activity"))
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.items[0].type").value("TASK_STATUS_CHANGED"))
				.andExpect(jsonPath("$.items[0].summary").value("moved \"Ship\" to Done"))
				.andExpect(jsonPath("$.items[0].actorName").value("Bob"))
				.andExpect(jsonPath("$.items[1].actorName").value("Alice"));
		as(bobToken, get("/api/tenants/" + tenant.getId() + "/activity?projectId=" + project.getId()))
				.andExpect(jsonPath("$.totalElements").value(2));

		as(carolToken, get("/api/tenants/" + tenant.getId() + "/activity")).andExpect(status().isForbidden());
		// Carol asking her own workspace for tenant 1's project finds nothing.
		as(carolToken, get("/api/tenants/" + otherTenant.getId() + "/activity?projectId=" + project.getId()))
				.andExpect(jsonPath("$.totalElements").value(0));
		as(bobToken, get("/api/tenants/" + tenant.getId() + "/activity?size=101")).andExpect(status().isBadRequest());
	}

	@Test
	void deletingAProjectKeepsItsHistoryAndDeletingTheWorkspaceRemovesIt() throws Exception {
		createTask("{\"title\":\"Ship\",\"status\":\"TODO\",\"priority\":\"LOW\",\"assigneeId\":\"" + bob.getId() + "\"}");
		as(aliceToken, delete("/api/tenants/" + tenant.getId() + "/projects/" + project.getId())).andExpect(status().isNoContent());

		as(aliceToken, get("/api/tenants/" + tenant.getId() + "/activity"))
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.items[0].summary").value("deleted project \"Apollo\" and its 1 task(s)"));
		assertThat(activityEventRepository.findAll()).allSatisfy(e -> assertThat(e.getProjectId()).isNull());

		as(aliceToken, delete("/api/tenants/" + tenant.getId())).andExpect(status().isNoContent());
		assertThat(activityEventRepository.count()).isZero();
		assertThat(notificationRepository.findAll()).allSatisfy(n -> assertThat(n.getTenantId()).isNull());
	}
}
