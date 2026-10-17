package io.github.aryan52.tenantflow.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.aryan52.tenantflow.activity.ActivityEventRepository;
import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.Project;
import io.github.aryan52.tenantflow.entity.Role;
import io.github.aryan52.tenantflow.entity.Task;
import io.github.aryan52.tenantflow.entity.TaskPriority;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import io.github.aryan52.tenantflow.entity.Tenant;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.notification.NotificationRepository;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import io.github.aryan52.tenantflow.repository.TaskRepository;
import io.github.aryan52.tenantflow.repository.TenantRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.security.JwtService;
import java.time.LocalDate;
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
		"spring.datasource.url=jdbc:h2:mem:tenantflow_comments;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-comment-secret-test-comment-secret-test-comment-x"
})
@AutoConfigureMockMvc
class TaskCommentIntegrationTests {

	@Autowired private MockMvc mockMvc;
	@Autowired private UserRepository userRepository;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private MembershipRepository membershipRepository;
	@Autowired private ProjectRepository projectRepository;
	@Autowired private TaskRepository taskRepository;
	@Autowired private TaskCommentRepository commentRepository;
	@Autowired private NotificationRepository notificationRepository;
	@Autowired private ActivityEventRepository activityRepository;
	@Autowired private JwtService jwtService;

	private User admin;
	private User member;
	private User assignee;
	private Tenant tenant;
	private Project project;
	private Project otherProject;
	private Task task;
	private Task otherTask;
	private String adminToken;
	private String memberToken;
	private String assigneeToken;
	private String outsiderToken;

	@BeforeEach
	void setup() {
		notificationRepository.deleteAll();
		activityRepository.deleteAll();
		commentRepository.deleteAll();
		taskRepository.deleteAll();
		projectRepository.deleteAll();
		membershipRepository.deleteAll();
		tenantRepository.deleteAll();
		userRepository.deleteAll();

		admin = userRepository.save(User.builder().email("admin@c.test").name("Ada").passwordHash("h").build());
		member = userRepository.save(User.builder().email("member@c.test").name("Max").passwordHash("h").build());
		assignee = userRepository.save(User.builder().email("assignee@c.test").name("Ava").passwordHash("h").build());
		User outsider = userRepository.save(User.builder().email("out@c.test").name("Oz").passwordHash("h").build());
		adminToken = jwtService.generateToken(admin);
		memberToken = jwtService.generateToken(member);
		assigneeToken = jwtService.generateToken(assignee);
		outsiderToken = jwtService.generateToken(outsider);

		tenant = tenantRepository.save(Tenant.builder().name("T").slug("t-comments").build());
		membershipRepository.save(Membership.builder().user(admin).tenant(tenant).role(Role.ADMIN).build());
		membershipRepository.save(Membership.builder().user(member).tenant(tenant).role(Role.MEMBER).build());
		membershipRepository.save(Membership.builder().user(assignee).tenant(tenant).role(Role.MEMBER).build());
		Tenant other = tenantRepository.save(Tenant.builder().name("O").slug("o-comments").build());
		membershipRepository.save(Membership.builder().user(outsider).tenant(other).role(Role.OWNER).build());

		project = projectRepository.save(Project.builder().tenant(tenant).name("P").build());
		otherProject = projectRepository.save(Project.builder().tenant(tenant).name("Q").build());
		task = taskRepository.save(Task.builder().project(project).title("Write spec").status(TaskStatus.TODO)
				.priority(TaskPriority.MEDIUM).assignee(assignee).createdBy(admin).dueDate(LocalDate.now().minusDays(1)).build());
		otherTask = taskRepository.save(Task.builder().project(otherProject).title("Other").status(TaskStatus.TODO)
				.priority(TaskPriority.LOW).build());
	}

	private String url(Project p, Task t) {
		return "/api/tenants/" + tenant.getId() + "/projects/" + p.getId() + "/tasks/" + t.getId() + "/comments";
	}

	private ResultActions postComment(String token, String body) throws Exception {
		return mockMvc.perform(post(url(project, task)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":" + body + "}"));
	}

	private String createdId(String token, String text) throws Exception {
		String json = postComment(token, "\"" + text + "\"").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
	}

	@Test
	void membersDiscussATaskAndTheAssigneeAndCreatorAreNotified() throws Exception {
		postComment(memberToken, "\"  Draft is ready for review  \"")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.body").value("Draft is ready for review"))
				.andExpect(jsonPath("$.authorName").value("Max"))
				.andExpect(jsonPath("$.edited").value(false));
		createdId(assigneeToken, "Thanks, looking now");

		mockMvc.perform(get(url(project, task)).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[*].body").value(contains("Draft is ready for review", "Thanks, looking now")))
				.andExpect(jsonPath("$.totalElements").value(2));

		// First comment notifies assignee + creator; the second (by the assignee) only the creator.
		assertThat(notificationRepository.countUnread(assignee.getId())).isEqualTo(1);
		assertThat(notificationRepository.countUnread(admin.getId())).isEqualTo(2);
		assertThat(notificationRepository.countUnread(member.getId())).isZero();

		// The comments appear in this task's history, and only there.
		mockMvc.perform(get("/api/tenants/" + tenant.getId() + "/activity").param("taskId", task.getId().toString())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[*].type").value(hasItem("TASK_COMMENTED")))
				.andExpect(jsonPath("$.totalElements").value(2));
		mockMvc.perform(get("/api/tenants/" + tenant.getId() + "/activity").param("taskId", otherTask.getId().toString())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void invalidCommentsAreRejected() throws Exception {
		postComment(memberToken, "\"   \"").andExpect(status().isBadRequest());
		postComment(memberToken, "\"" + "x".repeat(TaskComment.BODY_MAX + 1) + "\"").andExpect(status().isBadRequest());
		postComment(memberToken, "null").andExpect(status().isBadRequest());
		assertThat(commentRepository.count()).isZero();
	}

	@Test
	void commentsAreScopedToWorkspaceProjectAndTask() throws Exception {
		String id = createdId(memberToken, "Scoped");
		mockMvc.perform(get(url(project, task)).header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(post(url(project, task)).header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken)
						.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"hi\"}"))
				.andExpect(status().isForbidden());
		// The task exists, but not in this project: not found.
		mockMvc.perform(get(url(otherProject, task)).header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isNotFound());
		// The comment exists, but on another task: not found.
		mockMvc.perform(delete(url(otherProject, otherTask) + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isNotFound());
		mockMvc.perform(get(url(project, task)))
				.andExpect(status().isUnauthorized());
		assertThat(commentRepository.count()).isEqualTo(1);
	}

	@Test
	void onlyTheAuthorEditsAndTheAuthorOrAnAdminDeletes() throws Exception {
		String id = createdId(memberToken, "Original");
		mockMvc.perform(put(url(project, task) + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Hijacked\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(put(url(project, task) + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
						.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Revised\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body").value("Revised"));

		mockMvc.perform(delete(url(project, task) + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + assigneeToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete(url(project, task) + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isNoContent());

		String own = createdId(assigneeToken, "Mine");
		mockMvc.perform(delete(url(project, task) + "/" + own).header(HttpHeaders.AUTHORIZATION, "Bearer " + assigneeToken))
				.andExpect(status().isNoContent());
		assertThat(commentRepository.count()).isZero();
	}

	@Test
	void deletingATaskRemovesItsDiscussion() throws Exception {
		createdId(memberToken, "Will be removed");
		mockMvc.perform(delete("/api/tenants/" + tenant.getId() + "/projects/" + project.getId() + "/tasks/" + task.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isNoContent());
		assertThat(commentRepository.count()).isZero();
	}

	@Test
	void memberListShowsEachPersonsOpenAndOverdueWork() throws Exception {
		mockMvc.perform(get("/api/tenants/" + tenant.getId() + "/members").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == 'Ava')].openTasks").value(hasItem(1)))
				.andExpect(jsonPath("$[?(@.name == 'Ava')].overdueTasks").value(hasItem(1)))
				.andExpect(jsonPath("$[?(@.name == 'Max')].openTasks").value(hasItem(0)))
				.andExpect(jsonPath("$[*].openTasks").value(not(hasItem((Object) null))));
	}
}
