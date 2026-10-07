package io.github.aryan52.tenantflow.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import java.time.LocalDate;
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
	private Project otherProject;
	private String adminToken;
	private String memberToken;
	private String otherToken;

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

		project = projectRepository.save(Project.builder().tenant(tenant).name("P1").build());
		otherProject = projectRepository.save(Project.builder().tenant(otherTenant).name("Foreign").build());
	}

	private String tasksUrl(Tenant t, Project p) {
		return "/api/tenants/" + t.getId() + "/projects/" + p.getId() + "/tasks";
	}

	private Task saveTask(Project p, String title) {
		return taskRepository.save(Task.builder().project(p).title(title).status(TaskStatus.TODO).priority(TaskPriority.LOW).build());
	}

	@Test
	void memberCanCreateTask() throws Exception {
		CreateTaskRequest request = new CreateTaskRequest("T1", "Desc", TaskStatus.TODO, TaskPriority.HIGH, memberUser.getId(), null);

		mockMvc.perform(post(tasksUrl(tenant, project))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("T1"))
				.andExpect(jsonPath("$.assigneeId").value(memberUser.getId().toString()))
				.andExpect(jsonPath("$.assigneeName").value("M"));

		assertThat(taskRepository.count()).isEqualTo(1);
	}

	@Test
	void createRecordsCreatorDueDateAndUrgentPriority() throws Exception {
		CreateTaskRequest request = new CreateTaskRequest("Ship it", null, TaskStatus.IN_PROGRESS, TaskPriority.URGENT, null, LocalDate.of(2026, 3, 15));

		mockMvc.perform(post(tasksUrl(tenant, project))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.priority").value("URGENT"))
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.dueDate").value("2026-03-15"))
				.andExpect(jsonPath("$.createdById").value(memberUser.getId().toString()))
				.andExpect(jsonPath("$.createdByName").value("M"))
				.andExpect(jsonPath("$.assigneeId").doesNotExist());
	}

	@Test
	void createValidation() throws Exception {
		mockMvc.perform(post(tasksUrl(tenant, project))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\" \",\"status\":\"TODO\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.title").exists())
				.andExpect(jsonPath("$.fieldErrors.priority").exists());

		mockMvc.perform(post(tasksUrl(tenant, project))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"x\",\"status\":\"TODO\",\"priority\":\"CRITICAL\"}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post(tasksUrl(tenant, project))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"x\",\"status\":\"TODO\",\"priority\":\"LOW\",\"dueDate\":\"not-a-date\"}"))
				.andExpect(status().isBadRequest());

		assertThat(taskRepository.count()).isZero();
	}

	@Test
	void assignToNonMemberRejected() throws Exception {
		CreateTaskRequest request = new CreateTaskRequest("T1", "Desc", TaskStatus.TODO, TaskPriority.HIGH, otherUser.getId(), null);

		mockMvc.perform(post(tasksUrl(tenant, project))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest());

		assertThat(taskRepository.count()).isZero();
	}

	@Test
	void listTasks() throws Exception {
		saveTask(project, "T1");
		saveTask(otherProject, "Not mine");

		mockMvc.perform(get(tasksUrl(tenant, project))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].title").value("T1"));
	}

	@Test
	void updateChangesPriorityDueDateAndAssignee() throws Exception {
		Task task = saveTask(project, "T1");

		UpdateTaskRequest assign = new UpdateTaskRequest("T1 edited", "d", TaskStatus.IN_PROGRESS, TaskPriority.URGENT,
				memberUser.getId(), LocalDate.of(2026, 1, 2), null);
		mockMvc.perform(put(tasksUrl(tenant, project) + "/" + task.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(assign)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("T1 edited"))
				.andExpect(jsonPath("$.priority").value("URGENT"))
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.assigneeId").value(memberUser.getId().toString()))
				.andExpect(jsonPath("$.dueDate").value("2026-01-02"));

		// Reassign, then clear the due date and unassign.
		UpdateTaskRequest reassign = new UpdateTaskRequest("T1 edited", "d", TaskStatus.IN_PROGRESS, TaskPriority.URGENT,
				adminUser.getId(), LocalDate.of(2026, 1, 2), null);
		mockMvc.perform(put(tasksUrl(tenant, project) + "/" + task.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(reassign)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.assigneeId").value(adminUser.getId().toString()));

		UpdateTaskRequest clear = new UpdateTaskRequest("T1 edited", null, TaskStatus.DONE, TaskPriority.LOW, null, null, null);
		mockMvc.perform(put(tasksUrl(tenant, project) + "/" + task.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(clear)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.assigneeId").doesNotExist())
				.andExpect(jsonPath("$.dueDate").doesNotExist())
				.andExpect(jsonPath("$.status").value("DONE"));

		Task stored = taskRepository.findById(task.getId()).orElseThrow();
		assertThat(stored.getDueDate()).isNull();
		assertThat(stored.getAssignee()).isNull();
	}

	@Test
	void updateKeepsAssigneeWhoLeftWorkspaceButRejectsNewNonMember() throws Exception {
		Membership leaving = membershipRepository.findByUser_IdAndTenant_Id(memberUser.getId(), tenant.getId()).orElseThrow();
		Task task = taskRepository.save(Task.builder().project(project).title("T1").status(TaskStatus.TODO)
				.priority(TaskPriority.LOW).assignee(memberUser).build());
		membershipRepository.delete(leaving);

		UpdateTaskRequest unchanged = new UpdateTaskRequest("Renamed", null, TaskStatus.TODO, TaskPriority.LOW, memberUser.getId(), null, null);
		mockMvc.perform(put(tasksUrl(tenant, project) + "/" + task.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(unchanged)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.assigneeId").value(memberUser.getId().toString()));

		UpdateTaskRequest outsider = new UpdateTaskRequest("Renamed", null, TaskStatus.TODO, TaskPriority.LOW, otherUser.getId(), null, null);
		mockMvc.perform(put(tasksUrl(tenant, project) + "/" + task.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(outsider)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void patchStatusOnlyChangesStatus() throws Exception {
		Task task = taskRepository.save(Task.builder().project(project).title("T1").description("keep")
				.status(TaskStatus.TODO).priority(TaskPriority.HIGH).dueDate(LocalDate.of(2026, 5, 1)).build());

		mockMvc.perform(patch(tasksUrl(tenant, project) + "/" + task.getId() + "/status")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"IN_PROGRESS\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.priority").value("HIGH"))
				.andExpect(jsonPath("$.description").value("keep"))
				.andExpect(jsonPath("$.dueDate").value("2026-05-01"));

		mockMvc.perform(patch(tasksUrl(tenant, project) + "/" + task.getId() + "/status")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(patch(tasksUrl(tenant, project) + "/" + task.getId() + "/status")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"ARCHIVED\"}"))
				.andExpect(status().isBadRequest());

		assertThat(taskRepository.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
	}

	@Test
	void moveTaskToAnotherProjectInSameTenant() throws Exception {
		Project target = projectRepository.save(Project.builder().tenant(tenant).name("P2").build());
		Task task = saveTask(project, "Movable");

		UpdateTaskRequest move = new UpdateTaskRequest("Movable", null, TaskStatus.TODO, TaskPriority.LOW, null, null, target.getId());
		mockMvc.perform(put(tasksUrl(tenant, project) + "/" + task.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(move)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.projectId").value(target.getId().toString()));

		mockMvc.perform(get(tasksUrl(tenant, project))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get(tasksUrl(tenant, target) + "/" + task.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isOk());
	}

	@Test
	void cannotMoveTaskIntoAnotherTenantsProject() throws Exception {
		Task task = saveTask(project, "Stay");

		UpdateTaskRequest move = new UpdateTaskRequest("Stay", null, TaskStatus.TODO, TaskPriority.LOW, null, null, otherProject.getId());
		mockMvc.perform(put(tasksUrl(tenant, project) + "/" + task.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(move)))
				.andExpect(status().isNotFound());

		assertThat(taskRepository.findById(task.getId()).orElseThrow().getProject().getId()).isEqualTo(project.getId());
	}

	@Test
	void crossWorkspaceAccessIsBlocked() throws Exception {
		Task task = saveTask(project, "Private");
		String body = objectMapper.writeValueAsString(
				new UpdateTaskRequest("Hacked", null, TaskStatus.DONE, TaskPriority.LOW, null, null, null));

		// A user of another tenant cannot touch tenant 1 at all.
		mockMvc.perform(get(tasksUrl(tenant, project))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(put(tasksUrl(tenant, project) + "/" + task.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isForbidden());
		mockMvc.perform(patch(tasksUrl(tenant, project) + "/" + task.getId() + "/status")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"DONE\"}"))
				.andExpect(status().isForbidden());

		// ...nor reach it by pairing their own tenant id with tenant 1's project/task ids.
		mockMvc.perform(get("/api/tenants/" + otherTenant.getId() + "/projects/" + project.getId() + "/tasks")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
				.andExpect(status().isNotFound());
		mockMvc.perform(put("/api/tenants/" + otherTenant.getId() + "/projects/" + otherProject.getId() + "/tasks/" + task.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/tenants/" + otherTenant.getId() + "/projects/" + otherProject.getId() + "/tasks/" + task.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
				.andExpect(status().isNotFound());

		Task stored = taskRepository.findById(task.getId()).orElseThrow();
		assertThat(stored.getTitle()).isEqualTo("Private");
		assertThat(stored.getStatus()).isEqualTo(TaskStatus.TODO);
	}

	@Test
	void taskIsOnlyReachableThroughItsOwnProject() throws Exception {
		Project sibling = projectRepository.save(Project.builder().tenant(tenant).name("P2").build());
		Task task = saveTask(project, "T1");

		mockMvc.perform(get(tasksUrl(tenant, sibling) + "/" + task.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isNotFound());
		mockMvc.perform(patch(tasksUrl(tenant, sibling) + "/" + task.getId() + "/status")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"DONE\"}"))
				.andExpect(status().isNotFound());

		assertThat(taskRepository.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.TODO);
	}

	@Test
	void adminCanDeleteTask() throws Exception {
		Task task = saveTask(project, "T1");

		mockMvc.perform(delete(tasksUrl(tenant, project) + "/" + task.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isNoContent());

		assertThat(taskRepository.findById(task.getId())).isEmpty();
	}

	@Test
	void memberCannotDeleteTask() throws Exception {
		Task task = saveTask(project, "T1");

		mockMvc.perform(delete(tasksUrl(tenant, project) + "/" + task.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isForbidden());

		assertThat(taskRepository.findById(task.getId())).isPresent();
	}
}
