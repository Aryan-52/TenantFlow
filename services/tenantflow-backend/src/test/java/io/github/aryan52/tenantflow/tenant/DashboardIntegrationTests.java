package io.github.aryan52.tenantflow.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_dash_api;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
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
class DashboardIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

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
	private Tenant tenant;
	private String adminToken;
	private String memberToken;
	private String outsiderToken;

	@BeforeEach
	void setup() {
		taskRepository.deleteAll();
		projectRepository.deleteAll();
		membershipRepository.deleteAll();
		tenantRepository.deleteAll();
		userRepository.deleteAll();

		adminUser = userRepository.save(User.builder().email("admin@example.com").name("A").passwordHash("hash").build());
		adminToken = jwtService.generateToken(adminUser);

		tenant = tenantRepository.save(Tenant.builder().name("T1").slug("t-1").build());
		membershipRepository.save(Membership.builder().user(adminUser).tenant(tenant).role(Role.ADMIN).build());

		Project p1 = projectRepository.save(Project.builder().tenant(tenant).name("P1").build());
		Project p2 = projectRepository.save(Project.builder().tenant(tenant).name("P2").build());

		taskRepository.save(Task.builder().project(p1).title("T1").status(TaskStatus.TODO).priority(TaskPriority.LOW).build());
		taskRepository.save(Task.builder().project(p1).title("T2").status(TaskStatus.IN_PROGRESS).priority(TaskPriority.MEDIUM).build());
		taskRepository.save(Task.builder().project(p2).title("T3").status(TaskStatus.DONE).priority(TaskPriority.HIGH).build());
	}

	/** Adds a member, dated tasks and a second workspace whose data must never leak in. */
	private void seedWorkload() {
		memberUser = userRepository.save(User.builder().email("member@example.com").name("Mia").passwordHash("hash").build());
		memberToken = jwtService.generateToken(memberUser);
		membershipRepository.save(Membership.builder().user(memberUser).tenant(tenant).role(Role.MEMBER).build());
		User outsider = userRepository.save(User.builder().email("out@example.com").name("O").passwordHash("hash").build());
		outsiderToken = jwtService.generateToken(outsider);

		LocalDate today = LocalDate.now();
		Project p3 = projectRepository.save(Project.builder().tenant(tenant).name("P3").build());
		task(p3, "Late", TaskStatus.TODO, today.minusDays(2), memberUser);
		task(p3, "Today", TaskStatus.IN_PROGRESS, today, memberUser);
		task(p3, "Next week", TaskStatus.TODO, today.plusDays(7), null);
		task(p3, "Later", TaskStatus.TODO, today.plusDays(8), memberUser);
		task(p3, "Undated", TaskStatus.TODO, null, memberUser);
		task(p3, "Done late", TaskStatus.DONE, today.minusDays(5), memberUser);
		task(p3, "Admin's", TaskStatus.TODO, today.plusDays(1), adminUser);

		Tenant other = tenantRepository.save(Tenant.builder().name("Other").slug("other").build());
		membershipRepository.save(Membership.builder().user(memberUser).tenant(other).role(Role.OWNER).build());
		Project foreign = projectRepository.save(Project.builder().tenant(other).name("Foreign").build());
		task(foreign, "Foreign late", TaskStatus.TODO, today.minusDays(1), memberUser);
		task(foreign, "Foreign soon", TaskStatus.TODO, today.plusDays(1), memberUser);
	}

	private void task(Project project, String title, TaskStatus status, LocalDate due, User assignee) {
		taskRepository.save(Task.builder().project(project).title(title).status(status)
				.priority(TaskPriority.MEDIUM).dueDate(due).assignee(assignee).build());
	}

	@Test
	void getDashboardData() throws Exception {
		mockMvc.perform(get("/api/tenants/" + tenant.getId() + "/dashboard")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalProjects").value(2))
				.andExpect(jsonPath("$.totalTasks").value(3))
				.andExpect(jsonPath("$.todoTasks").value(1))
				.andExpect(jsonPath("$.inProgressTasks").value(1))
				.andExpect(jsonPath("$.doneTasks").value(1))
				.andExpect(jsonPath("$.totalMembers").value(1))
				.andExpect(jsonPath("$.currentUserRole").value("ADMIN"));
	}

	@Test
	void dashboardCombinesTenantCountsWithTheCurrentUsersWork() throws Exception {
		seedWorkload();
		mockMvc.perform(get("/api/tenants/" + tenant.getId() + "/dashboard")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentUserRole").value("MEMBER"))
				.andExpect(jsonPath("$.overdueTasks").value(1))
				// Today, Admin's, Next week (day 7 is inside the window; day 8 is not).
				.andExpect(jsonPath("$.dueSoonTasks").value(3))
				.andExpect(jsonPath("$.myOpenTasks").value(4))
				// Soonest first, undated last, done and other users' tasks excluded.
				.andExpect(jsonPath("$.myTasks[*].title").value(contains("Late", "Today", "Later", "Undated")))
				.andExpect(jsonPath("$.myTasks[0].projectName").value("P3"))
				.andExpect(jsonPath("$.upcomingTasks[*].title").value(contains("Late", "Today", "Admin's", "Next week")))
				.andExpect(jsonPath("$.recentProjects.length()").value(3))
				.andExpect(jsonPath("$.recentProjects[0].name").value("P3"))
				.andExpect(jsonPath("$.recentProjects[0].totalTasks").value(7))
				.andExpect(jsonPath("$.recentProjects[0].completedTasks").value(1));

		// The same workspace seen by another user: same tenant counts, different "my work".
		mockMvc.perform(get("/api/tenants/" + tenant.getId() + "/dashboard")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(jsonPath("$.overdueTasks").value(1))
				.andExpect(jsonPath("$.currentUserRole").value("ADMIN"))
				.andExpect(jsonPath("$.myOpenTasks").value(1))
				.andExpect(jsonPath("$.myTasks[*].title").value(contains("Admin's")));
	}

	@Test
	void tenantTaskViewsAreScopedToTheWorkspaceAndFocus() throws Exception {
		seedWorkload();
		String url = "/api/tenants/" + tenant.getId() + "/tasks";
		String bearer = "Bearer " + memberToken;

		mockMvc.perform(get(url).param("focus", "overdue").header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[*].title").value(contains("Late")))
				.andExpect(jsonPath("$.totalElements").value(1));
		mockMvc.perform(get(url).param("focus", "due-soon").header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(jsonPath("$.items[*].title").value(contains("Today", "Admin's", "Next week")));
		mockMvc.perform(get(url).param("focus", "mine").header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(jsonPath("$.items[*].title").value(contains("Late", "Today", "Later", "Undated")));
		mockMvc.perform(get(url).param("size", "2").header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.totalElements").value(8)) // T1, T2 + 6 open in P3
				.andExpect(jsonPath("$.hasNext").value(true));
	}

	@Test
	void tenantTaskViewsRejectOutsidersAndUnknownFocus() throws Exception {
		seedWorkload();
		String url = "/api/tenants/" + tenant.getId() + "/tasks";
		mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/tenants/" + tenant.getId() + "/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(url))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get(url).param("focus", "everything").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get(url).param("size", "101").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isBadRequest());
	}
}
