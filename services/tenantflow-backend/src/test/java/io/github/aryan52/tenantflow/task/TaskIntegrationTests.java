package io.github.aryan52.tenantflow.task;

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
import io.github.aryan52.tenantflow.entity.Role;
import io.github.aryan52.tenantflow.entity.Task;
import io.github.aryan52.tenantflow.entity.TaskPriority;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import io.github.aryan52.tenantflow.entity.Tenant;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import io.github.aryan52.tenantflow.repository.TaskRepository;
import io.github.aryan52.tenantflow.repository.TenantRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.security.JwtService;
import io.github.aryan52.tenantflow.task.dto.CreateTaskRequest;
import io.github.aryan52.tenantflow.task.dto.UpdateTaskRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_task_api;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
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
class TaskIntegrationTests {

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
	private Project project;
	private String adminToken;
	private String memberToken;

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

		tenant = tenantRepository.save(Tenant.builder().name("T1").slug("t-1").build());
		otherTenant = tenantRepository.save(Tenant.builder().name("T2").slug("t-2").build());

		membershipRepository.save(Membership.builder().user(adminUser).tenant(tenant).role(Role.ADMIN).build());
		membershipRepository.save(Membership.builder().user(memberUser).tenant(tenant).role(Role.MEMBER).build());

		project = projectRepository.save(Project.builder().tenant(tenant).name("P1").build());
	}

	@Test
	void memberCanCreateTask() throws Exception {
		CreateTaskRequest request = new CreateTaskRequest("T1", "Desc", TaskStatus.TODO, TaskPriority.HIGH, memberUser.getId());

		mockMvc.perform(post("/api/tenants/" + tenant.getId() + "/projects/" + project.getId() + "/tasks")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("T1"))
				.andExpect(jsonPath("$.assigneeId").value(memberUser.getId().toString()));

		assertThat(taskRepository.count()).isEqualTo(1);
	}

	@Test
	void assignToNonMemberRejected() throws Exception {
		CreateTaskRequest request = new CreateTaskRequest("T1", "Desc", TaskStatus.TODO, TaskPriority.HIGH, otherUser.getId());

		mockMvc.perform(post("/api/tenants/" + tenant.getId() + "/projects/" + project.getId() + "/tasks")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest()); // Exception mapped to 400 or 500 depending on handler
	}

	@Test
	void listTasks() throws Exception {
		taskRepository.save(Task.builder().project(project).title("T1").status(TaskStatus.TODO).priority(TaskPriority.LOW).build());

		mockMvc.perform(get("/api/tenants/" + tenant.getId() + "/projects/" + project.getId() + "/tasks")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].title").value("T1"));
	}

	@Test
	void adminCanDeleteTask() throws Exception {
		Task task = taskRepository.save(Task.builder().project(project).title("T1").status(TaskStatus.TODO).priority(TaskPriority.LOW).build());

		mockMvc.perform(delete("/api/tenants/" + tenant.getId() + "/projects/" + project.getId() + "/tasks/" + task.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isNoContent());

		assertThat(taskRepository.findById(task.getId())).isEmpty();
	}

	@Test
	void memberCannotDeleteTask() throws Exception {
		Task task = taskRepository.save(Task.builder().project(project).title("T1").status(TaskStatus.TODO).priority(TaskPriority.LOW).build());

		mockMvc.perform(delete("/api/tenants/" + tenant.getId() + "/projects/" + project.getId() + "/tasks/" + task.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isForbidden());
	}
}
