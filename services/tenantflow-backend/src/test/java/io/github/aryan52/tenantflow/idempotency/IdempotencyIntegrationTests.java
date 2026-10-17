package io.github.aryan52.tenantflow.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.aryan52.tenantflow.activity.ActivityEventRepository;
import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.Project;
import io.github.aryan52.tenantflow.entity.Role;
import io.github.aryan52.tenantflow.entity.Tenant;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.notification.NotificationRepository;
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
		"spring.datasource.url=jdbc:h2:mem:tenantflow_idempotency;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-idempotency-secret-test-idempotency-secret-test"
})
@AutoConfigureMockMvc
class IdempotencyIntegrationTests {

	@Autowired private MockMvc mockMvc;
	@Autowired private UserRepository userRepository;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private MembershipRepository membershipRepository;
	@Autowired private ProjectRepository projectRepository;
	@Autowired private TaskRepository taskRepository;
	@Autowired private NotificationRepository notificationRepository;
	@Autowired private ActivityEventRepository activityEventRepository;
	@Autowired private IdempotencyRecordRepository idempotencyRecordRepository;
	@Autowired private JwtService jwtService;

	private User alice;
	private User bob;
	private Tenant tenant;
	private Project project;
	private String aliceToken;
	private String bobToken;

	@BeforeEach
	void setup() {
		idempotencyRecordRepository.deleteAll();
		notificationRepository.deleteAll();
		activityEventRepository.deleteAll();
		taskRepository.deleteAll();
		projectRepository.deleteAll();
		membershipRepository.deleteAll();
		tenantRepository.deleteAll();
		userRepository.deleteAll();
		alice = userRepository.save(User.builder().email("alice@example.com").name("Alice").passwordHash("h").build());
		bob = userRepository.save(User.builder().email("bob@example.com").name("Bob").passwordHash("h").build());
		tenant = tenantRepository.save(Tenant.builder().name("T").slug("t").build());
		membershipRepository.save(Membership.builder().user(alice).tenant(tenant).role(Role.OWNER).build());
		membershipRepository.save(Membership.builder().user(bob).tenant(tenant).role(Role.MEMBER).build());
		project = projectRepository.save(Project.builder().tenant(tenant).name("P").build());
		aliceToken = jwtService.generateToken(alice);
		bobToken = jwtService.generateToken(bob);
	}

	private ResultActions createTask(String token, String key, String title) throws Exception {
		var req = post("/api/tenants/" + tenant.getId() + "/projects/" + project.getId() + "/tasks")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"" + title + "\",\"status\":\"TODO\",\"priority\":\"LOW\",\"assigneeId\":\"" + bob.getId() + "\"}");
		if (key != null) {
			req.header("Idempotency-Key", key);
		}
		return mockMvc.perform(req);
	}

	private String id(ResultActions r) throws Exception {
		return JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id");
	}

	@Test
	void aRetriedCreateReturnsTheOriginalTaskAndNotifiesOnce() throws Exception {
		String first = id(createTask(aliceToken, "create-task-0001", "Ship").andExpect(status().isCreated()));
		String second = id(createTask(aliceToken, "create-task-0001", "Ship").andExpect(status().isCreated()));

		assertThat(second).isEqualTo(first);
		assertThat(taskRepository.count()).isEqualTo(1);
		assertThat(notificationRepository.count()).isEqualTo(1);
		assertThat(activityEventRepository.count()).isEqualTo(1);
	}

	@Test
	void reusingAKeyForADifferentRequestIsRejected() throws Exception {
		createTask(aliceToken, "create-task-0002", "Ship").andExpect(status().isCreated());
		createTask(aliceToken, "create-task-0002", "Something else")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("This Idempotency-Key was already used with a different request"));
		assertThat(taskRepository.count()).isEqualTo(1);
	}

	@Test
	void keysArePerUserAndOptional() throws Exception {
		createTask(aliceToken, "shared-key-0003", "Ship").andExpect(status().isCreated());
		createTask(bobToken, "shared-key-0003", "Ship").andExpect(status().isCreated());
		createTask(aliceToken, null, "Ship").andExpect(status().isCreated());
		createTask(aliceToken, null, "Ship").andExpect(status().isCreated());
		assertThat(taskRepository.count()).isEqualTo(4);
	}

	@Test
	void malformedKeysAreRejected() throws Exception {
		createTask(aliceToken, "short", "Ship").andExpect(status().isBadRequest());
		createTask(aliceToken, "has spaces in it", "Ship").andExpect(status().isBadRequest());
		assertThat(taskRepository.count()).isZero();
	}

	@Test
	void projectCreationIsIdempotentToo() throws Exception {
		var req = post("/api/tenants/" + tenant.getId() + "/projects")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken)
				.header("Idempotency-Key", "create-project-0001")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Apollo\"}");
		String first = id(mockMvc.perform(req).andExpect(status().isCreated()));
		String second = id(mockMvc.perform(req).andExpect(status().isCreated()));
		assertThat(second).isEqualTo(first);
		assertThat(projectRepository.countByTenant_Id(tenant.getId())).isEqualTo(2); // "P" + "Apollo"
	}
}
