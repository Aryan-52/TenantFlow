package io.github.aryan52.tenantflow.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.aryan52.tenantflow.activity.ActivityEventRepository;
import io.github.aryan52.tenantflow.attachment.Attachment;
import io.github.aryan52.tenantflow.attachment.AttachmentRepository;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Status and assignment notifications, plus member/file search scoping. */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_notify_lifecycle;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-notify-lifecycle-secret-test-notify-lifecycle-x"
})
@AutoConfigureMockMvc
class NotificationLifecycleIntegrationTests {

	@Autowired private MockMvc mockMvc;
	@Autowired private UserRepository userRepository;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private MembershipRepository membershipRepository;
	@Autowired private ProjectRepository projectRepository;
	@Autowired private TaskRepository taskRepository;
	@Autowired private NotificationRepository notificationRepository;
	@Autowired private ActivityEventRepository activityRepository;
	@Autowired private AttachmentRepository attachmentRepository;
	@Autowired private JwtService jwtService;

	private User creator;
	private User ann;
	private User ben;
	private Tenant tenant;
	private Tenant other;
	private Project project;
	private Task task;

	@BeforeEach
	void setup() {
		attachmentRepository.deleteAll();
		notificationRepository.deleteAll();
		activityRepository.deleteAll();
		taskRepository.deleteAll();
		projectRepository.deleteAll();
		membershipRepository.deleteAll();
		tenantRepository.deleteAll();
		userRepository.deleteAll();
		creator = userRepository.save(User.builder().email("cleo@n.test").name("Cleo Creator").passwordHash("h").build());
		ann = userRepository.save(User.builder().email("ann@n.test").name("Ann Archer").passwordHash("h").build());
		ben = userRepository.save(User.builder().email("ben@n.test").name("Ben Baker").passwordHash("h").build());
		User stranger = userRepository.save(User.builder().email("anna.stranger@n.test").name("Anna Stranger").passwordHash("h").build());
		tenant = tenantRepository.save(Tenant.builder().name("T").slug("t-nl").build());
		other = tenantRepository.save(Tenant.builder().name("Other").slug("o-nl").build());
		for (User u : new User[] {creator, ann, ben}) {
			membershipRepository.save(Membership.builder().user(u).tenant(tenant).role(Role.MEMBER).build());
		}
		membershipRepository.save(Membership.builder().user(stranger).tenant(other).role(Role.OWNER).build());
		project = projectRepository.save(Project.builder().tenant(tenant).name("Apollo").build());
		task = taskRepository.save(Task.builder().project(project).title("Annual report").status(TaskStatus.TODO)
				.priority(TaskPriority.MEDIUM).assignee(ann).createdBy(creator).build());
		Project secret = projectRepository.save(Project.builder().tenant(other).name("Secret").build());
		attachmentRepository.save(Attachment.builder().id(UUID.randomUUID()).tenantId(tenant.getId()).projectId(project.getId())
				.originalFilename("annual-budget.xlsx").contentType("application/zip").sizeBytes(10).sha256("x").storageKey("k1").build());
		attachmentRepository.save(Attachment.builder().id(UUID.randomUUID()).tenantId(other.getId()).projectId(secret.getId())
				.originalFilename("annual-secret.pdf").contentType("application/pdf").sizeBytes(10).sha256("y").storageKey("k2").build());
	}

	private String bearer(User u) {
		return "Bearer " + jwtService.generateToken(u);
	}

	private String taskUrl() {
		return "/api/tenants/" + tenant.getId() + "/projects/" + project.getId() + "/tasks/" + task.getId();
	}

	@Test
	void completingATaskTellsTheCreatorButNeverTheActor() throws Exception {
		mockMvc.perform(patch(taskUrl() + "/status").header(HttpHeaders.AUTHORIZATION, bearer(ann))
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}")).andExpect(status().isOk());
		assertThat(notificationRepository.countUnread(creator.getId())).isEqualTo(1);
		assertThat(notificationRepository.countUnread(ann.getId())).isZero();
		mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, bearer(creator)))
				.andExpect(jsonPath("$.items[0].title").value("Completed: Annual report"))
				.andExpect(jsonPath("$.items[0].link").value(org.hamcrest.Matchers.endsWith("/tasks?task=" + task.getId())));

		// Setting the same status again is not a change: no second notification.
		mockMvc.perform(patch(taskUrl() + "/status").header(HttpHeaders.AUTHORIZATION, bearer(ann))
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}")).andExpect(status().isOk());
		assertThat(notificationRepository.countUnread(creator.getId())).isEqualTo(1);
	}

	@Test
	void reassignmentTellsBothTheNewAndThePreviousAssignee() throws Exception {
		String body = "{\"title\":\"Annual report\",\"description\":\"\",\"status\":\"TODO\",\"priority\":\"MEDIUM\",\"assigneeId\":\""
				+ ben.getId() + "\",\"dueDate\":null}";
		mockMvc.perform(put(taskUrl()).header(HttpHeaders.AUTHORIZATION, bearer(creator))
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
		mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, bearer(ben)))
				.andExpect(jsonPath("$.items[*].title").value(contains("You were assigned: Annual report")));
		mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, bearer(ann)))
				.andExpect(jsonPath("$.items[*].title").value(contains("No longer assigned to you: Annual report")));
		assertThat(notificationRepository.countUnread(creator.getId())).isZero();
	}

	@Test
	void searchFindsMembersAndFilesOnlyInTheCallersWorkspaces() throws Exception {
		mockMvc.perform(get("/api/search").param("q", "ann").header(HttpHeaders.AUTHORIZATION, bearer(ben)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.members.items[*].name").value(contains("Ann Archer")))
				.andExpect(jsonPath("$.members.items[*].name").value(not(hasItem("Anna Stranger"))))
				.andExpect(jsonPath("$.files.items[*].filename").value(contains("annual-budget.xlsx")))
				.andExpect(jsonPath("$.files.items[0].projectName").value("Apollo"))
				.andExpect(jsonPath("$.tasks.items[*].title").value(contains("Annual report")));
		mockMvc.perform(get("/api/search").param("q", "ann").param("type", "FILE").header(HttpHeaders.AUTHORIZATION, bearer(ben)))
				.andExpect(jsonPath("$.files.totalElements").value(1))
				.andExpect(jsonPath("$.members").doesNotExist());
	}
}
