package io.github.aryan52.tenantflow.planning;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import io.github.aryan52.tenantflow.activity.ActivityEventRepository;
import io.github.aryan52.tenantflow.automation.AutomationRuleRepository;
import io.github.aryan52.tenantflow.automation.AutomationRunRepository;
import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.Project;
import io.github.aryan52.tenantflow.entity.Role;
import io.github.aryan52.tenantflow.entity.Task;
import io.github.aryan52.tenantflow.entity.TaskPriority;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import io.github.aryan52.tenantflow.entity.Tenant;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.notification.NotificationRepository;
import io.github.aryan52.tenantflow.preferences.NotificationPreferenceRepository;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import io.github.aryan52.tenantflow.repository.TaskRepository;
import io.github.aryan52.tenantflow.repository.TenantRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.security.JwtService;
import io.github.aryan52.tenantflow.task.TaskCommentRepository;
import io.github.aryan52.tenantflow.task.TaskFocusRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Two workspaces: "Acme" (admin Ada, member Max, assignee Ava; project Apollo) and "Other"
 * (owner Oz, project Zeus). Used by the TenantFlow 2.0 integration tests, which all verify
 * that nothing crosses from one workspace to the other.
 */
abstract class WorkspaceFixture {

	@Autowired protected MockMvc mockMvc;
	@Autowired protected UserRepository userRepository;
	@Autowired protected TenantRepository tenantRepository;
	@Autowired protected MembershipRepository membershipRepository;
	@Autowired protected ProjectRepository projectRepository;
	@Autowired protected TaskRepository taskRepository;
	@Autowired protected NotificationRepository notificationRepository;
	@Autowired protected ActivityEventRepository activityRepository;
	@Autowired protected MilestoneRepository milestoneRepository;
	@Autowired protected TaskDependencyRepository dependencyRepository;
	@Autowired protected TaskFocusRepository focusRepository;
	@Autowired protected TaskCommentRepository commentRepository;
	@Autowired protected AutomationRuleRepository ruleRepository;
	@Autowired protected AutomationRunRepository runRepository;
	@Autowired protected NotificationPreferenceRepository preferenceRepository;
	@Autowired protected JwtService jwtService;

	protected User ada;
	protected User max;
	protected User ava;
	protected User oz;
	protected Tenant acme;
	protected Tenant other;
	protected Project apollo;
	protected Project zeus;
	protected String adaToken;
	protected String maxToken;
	protected String avaToken;
	protected String ozToken;

	@BeforeEach
	void resetWorkspaces() {
		runRepository.deleteAll();
		ruleRepository.deleteAll();
		preferenceRepository.deleteAll();
		notificationRepository.deleteAll();
		activityRepository.deleteAll();
		commentRepository.deleteAll();
		focusRepository.deleteAll();
		dependencyRepository.deleteAll();
		taskRepository.deleteAll();
		milestoneRepository.deleteAll();
		projectRepository.deleteAll();
		membershipRepository.deleteAll();
		tenantRepository.deleteAll();
		userRepository.deleteAll();

		ada = userRepository.save(User.builder().email("ada@x.test").name("Ada").passwordHash("h").build());
		max = userRepository.save(User.builder().email("max@x.test").name("Max").passwordHash("h").build());
		ava = userRepository.save(User.builder().email("ava@x.test").name("Ava").passwordHash("h").build());
		oz = userRepository.save(User.builder().email("oz@x.test").name("Oz").passwordHash("h").build());
		adaToken = jwtService.generateToken(ada);
		maxToken = jwtService.generateToken(max);
		avaToken = jwtService.generateToken(ava);
		ozToken = jwtService.generateToken(oz);

		acme = tenantRepository.save(Tenant.builder().name("Acme").slug("acme-" + System.nanoTime()).build());
		other = tenantRepository.save(Tenant.builder().name("Other").slug("other-" + System.nanoTime()).build());
		membershipRepository.save(Membership.builder().user(ada).tenant(acme).role(Role.ADMIN).build());
		membershipRepository.save(Membership.builder().user(max).tenant(acme).role(Role.MEMBER).build());
		membershipRepository.save(Membership.builder().user(ava).tenant(acme).role(Role.MEMBER).build());
		membershipRepository.save(Membership.builder().user(oz).tenant(other).role(Role.OWNER).build());

		apollo = projectRepository.save(Project.builder().tenant(acme).name("Apollo").owner(ada).createdBy(ada).build());
		zeus = projectRepository.save(Project.builder().tenant(other).name("Zeus").owner(oz).createdBy(oz).build());
	}

	protected Task task(Project p, String title, TaskStatus status, User assignee, LocalDate due) {
		return taskRepository.save(Task.builder().project(p).title(title).status(status).priority(TaskPriority.MEDIUM)
				.assignee(assignee).createdBy(ada).dueDate(due).build());
	}

	protected String base(Tenant t) {
		return "/api/tenants/" + t.getId();
	}

	protected String taskUrl(Task t) {
		// Reading ids never initialises a lazy proxy, so this works for detached tasks too.
		Project p = projectRepository.findById(t.getProject().getId()).orElseThrow();
		return "/api/tenants/" + p.getTenant().getId() + "/projects/" + p.getId() + "/tasks/" + t.getId();
	}

	protected ResultActions getAs(String token, String url) throws Exception {
		return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	protected ResultActions postAs(String token, String url, String json) throws Exception {
		return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	protected ResultActions putAs(String token, String url, String json) throws Exception {
		return mockMvc.perform(put(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	protected ResultActions patchAs(String token, String url, String json) throws Exception {
		return mockMvc.perform(patch(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	protected ResultActions deleteAs(String token, String url) throws Exception {
		return mockMvc.perform(delete(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	protected ResultActions setStatus(String token, Task t, TaskStatus status) throws Exception {
		return patchAs(token, taskUrl(t) + "/status", "{\"status\":\"" + status + "\"}");
	}
}
