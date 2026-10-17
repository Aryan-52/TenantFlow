package io.github.aryan52.tenantflow.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.Project;
import io.github.aryan52.tenantflow.entity.ProjectStatus;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * One analytics scenario, run on H2 and on PostgreSQL. Every expected number below is
 * derived by hand from the seeded rows, so analytics must agree with the underlying data.
 */
abstract class AnalyticsScenario {

	@Autowired protected MockMvc mockMvc;
	@Autowired protected UserRepository userRepository;
	@Autowired protected TenantRepository tenantRepository;
	@Autowired protected MembershipRepository membershipRepository;
	@Autowired protected ProjectRepository projectRepository;
	@Autowired protected TaskRepository taskRepository;
	@Autowired protected JwtService jwtService;
	@Autowired protected DataSource dataSource;

	protected Tenant tenant;
	protected Project apollo;
	protected Project foreignProject;
	protected Task inProgressTask;
	protected String memberToken;
	protected String outsiderToken;

	private final LocalDate today = LocalDate.now();
	private JdbcTemplate jdbc;

	protected abstract void clean(JdbcTemplate jdbc);

	private Instant daysAgo(int days) {
		return today.minusDays(days).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant();
	}

	private Task task(Project p, String title, TaskStatus status, TaskPriority priority, User assignee, LocalDate due) {
		return taskRepository.save(Task.builder().project(p).title(title).status(status).priority(priority).assignee(assignee).dueDate(due).build());
	}

	private void times(Task t, Instant created, Instant completed, Instant updated) {
		jdbc.update("update tasks set created_at = ?, completed_at = ?, updated_at = ? where id = ?",
				Timestamp.from(created), completed == null ? null : Timestamp.from(completed), Timestamp.from(updated), t.getId());
	}

	@BeforeEach
	void seed() {
		jdbc = new JdbcTemplate(dataSource);
		clean(jdbc);

		User ann = userRepository.save(User.builder().email("ann@a.test").name("Ann").passwordHash("h").build());
		User ben = userRepository.save(User.builder().email("ben@a.test").name("Ben").passwordHash("h").build());
		User outsider = userRepository.save(User.builder().email("out@a.test").name("Out").passwordHash("h").build());
		memberToken = jwtService.generateToken(ben);
		outsiderToken = jwtService.generateToken(outsider);

		tenant = tenantRepository.save(Tenant.builder().name("T").slug("t-analytics").build());
		membershipRepository.save(Membership.builder().user(ann).tenant(tenant).role(Role.ADMIN).build());
		membershipRepository.save(Membership.builder().user(ben).tenant(tenant).role(Role.MEMBER).build());
		Tenant other = tenantRepository.save(Tenant.builder().name("O").slug("o-analytics").build());
		membershipRepository.save(Membership.builder().user(outsider).tenant(other).role(Role.OWNER).build());

		apollo = projectRepository.save(Project.builder().tenant(tenant).name("Apollo").build());
		projectRepository.save(Project.builder().tenant(tenant).name("Borealis").build());
		Project comet = projectRepository.save(Project.builder().tenant(tenant).name("Comet").status(ProjectStatus.COMPLETED).build());
		Project dusk = projectRepository.save(Project.builder().tenant(tenant).name("Dusk").status(ProjectStatus.ARCHIVED).build());
		Project echo = projectRepository.save(Project.builder().tenant(tenant).name("Echo").build());
		foreignProject = projectRepository.save(Project.builder().tenant(other).name("Foreign").build());

		Task t1 = task(apollo, "t1", TaskStatus.TODO, TaskPriority.HIGH, ann, today.minusDays(2));
		inProgressTask = task(apollo, "t2", TaskStatus.IN_PROGRESS, TaskPriority.MEDIUM, ann, today.plusDays(3));
		task(apollo, "t3", TaskStatus.TODO, TaskPriority.LOW, ann, today.plusDays(5));
		task(apollo, "t4", TaskStatus.TODO, TaskPriority.URGENT, null, today.plusDays(1));
		task(apollo, "t5", TaskStatus.TODO, TaskPriority.MEDIUM, ann, null);
		Task t6 = task(apollo, "t6", TaskStatus.DONE, TaskPriority.MEDIUM, ben, today.minusDays(5));
		Task t7 = task(apollo, "t7", TaskStatus.DONE, TaskPriority.LOW, ben, today.minusDays(10));
		Task t8 = task(comet, "t8", TaskStatus.DONE, TaskPriority.LOW, ben, null);
		Task t9 = task(echo, "t9", TaskStatus.TODO, TaskPriority.LOW, ben, null);
		task(foreignProject, "foreign", TaskStatus.TODO, TaskPriority.URGENT, outsider, today.minusDays(30));
		assertThat(dusk.getId()).isNotNull();
		assertThat(t1.getId()).isNotNull();

		// Completed on time (day before due) and late (a week after due), each 2 days after creation.
		times(t6, daysAgo(8), daysAgo(6), daysAgo(6));
		times(t7, daysAgo(5), daysAgo(3), daysAgo(3));
		// Completed before completion times were recorded.
		times(t8, daysAgo(40), null, daysAgo(40));
		// Echo: nothing has happened for 20 days.
		times(t9, daysAgo(20), null, daysAgo(20));
		jdbc.update("update projects set updated_at = ? where id = ?", Timestamp.from(daysAgo(20)), echo.getId());
	}

	private String url() {
		return "/api/tenants/" + tenant.getId() + "/analytics";
	}

	@Test
	void workspaceTotalsMatchTheSeededTasks() throws Exception {
		mockMvc.perform(get(url()).header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.projects.total").value(5))
				.andExpect(jsonPath("$.projects.byStatus.ACTIVE").value(3))
				.andExpect(jsonPath("$.projects.byStatus.COMPLETED").value(1))
				.andExpect(jsonPath("$.projects.byStatus.ARCHIVED").value(1))
				.andExpect(jsonPath("$.projects.byStatus.ON_HOLD").value(0))
				// Apollo 2/7 (25–49%), Comet 1/1 (100%), Echo 0/1 (0–24%), Borealis no tasks; archived Dusk excluded.
				.andExpect(jsonPath("$.projects.progress[*].count").value(contains(1, 1, 0, 1, 1)))
				.andExpect(jsonPath("$.tasks.total").value(9))
				.andExpect(jsonPath("$.tasks.byStatus.TODO").value(5))
				.andExpect(jsonPath("$.tasks.byStatus.IN_PROGRESS").value(1))
				.andExpect(jsonPath("$.tasks.byStatus.DONE").value(3))
				.andExpect(jsonPath("$.tasks.open").value(6))
				.andExpect(jsonPath("$.tasks.overdue").value(1))
				.andExpect(jsonPath("$.tasks.dueSoon").value(3))
				.andExpect(jsonPath("$.tasks.unassignedOpen").value(1))
				.andExpect(jsonPath("$.tasks.openWithoutDueDate").value(2))
				.andExpect(jsonPath("$.tasks.completionRate").value(3.0 / 9))
				.andExpect(jsonPath("$.tasks.openByPriority.URGENT").value(1))
				.andExpect(jsonPath("$.tasks.openByPriority.HIGH").value(1))
				.andExpect(jsonPath("$.tasks.openByPriority.MEDIUM").value(2))
				.andExpect(jsonPath("$.tasks.openByPriority.LOW").value(2));
	}

	@Test
	void workloadAndDeliveryAreComputedFromRecordedData() throws Exception {
		mockMvc.perform(get(url()).header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(jsonPath("$.workload[*].name").value(contains("Ann", "Ben", null)))
				.andExpect(jsonPath("$.workload[0].todo").value(3))
				.andExpect(jsonPath("$.workload[0].inProgress").value(1))
				.andExpect(jsonPath("$.workload[0].overdue").value(1))
				.andExpect(jsonPath("$.workload[2].todo").value(1))
				.andExpect(jsonPath("$.delivery.completed").value(2))
				.andExpect(jsonPath("$.delivery.onTime").value(1))
				.andExpect(jsonPath("$.delivery.late").value(1))
				.andExpect(jsonPath("$.delivery.onTimeRate").value(0.5))
				.andExpect(jsonPath("$.delivery.averageDaysToComplete").value(2.0))
				.andExpect(jsonPath("$.delivery.untrackedDone").value(1));
	}

	@Test
	void trendSumsMatchTheRange() throws Exception {
		String json = mockMvc.perform(get(url()).header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(jsonPath("$.range.bucket").value("WEEK"))
				.andReturn().getResponse().getContentAsString();
		java.util.List<Integer> created = com.jayway.jsonpath.JsonPath.read(json, "$.trend[*].created");
		java.util.List<Integer> completed = com.jayway.jsonpath.JsonPath.read(json, "$.trend[*].completed");
		java.util.List<Integer> due = com.jayway.jsonpath.JsonPath.read(json, "$.trend[*].due");
		// Default range is the last 56 days: every task was created in it; t1, t6 and t7 fell due in it.
		assertThat(created.stream().mapToInt(Integer::intValue).sum()).isEqualTo(9);
		assertThat(completed.stream().mapToInt(Integer::intValue).sum()).isEqualTo(2);
		assertThat(due.stream().mapToInt(Integer::intValue).sum()).isEqualTo(3);

		// A daily range only sees what happened in it.
		mockMvc.perform(get(url()).param("from", today.minusDays(4).toString()).param("to", today.toString())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(jsonPath("$.range.bucket").value("DAY"))
				.andExpect(jsonPath("$.trend.length()").value(5))
				.andExpect(jsonPath("$.delivery.completed").value(1));
	}

	@Test
	void projectHealthExplainsEverySignal() throws Exception {
		mockMvc.perform(get(url()).header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(jsonPath("$.health[*].name").value(contains("Apollo", "Echo", "Borealis")))
				.andExpect(jsonPath("$.health[0].signals[*].code").value(containsInAnyOrder("OVERDUE", "HIGH_PRIORITY", "DUE_SOON", "CONCENTRATION")))
				.andExpect(jsonPath("$.health[0].signals[?(@.code == 'CONCENTRATION')].message").value(contains("Ann holds 4 of 5 open tasks")))
				.andExpect(jsonPath("$.health[0].signals[?(@.code == 'HIGH_PRIORITY')].message").value(contains("2 urgent or overdue high-priority tasks open")))
				.andExpect(jsonPath("$.health[1].signals[0].code").value("STALE"))
				.andExpect(jsonPath("$.health[1].signals[0].message").value("No updates in 20 days with 1 open task"))
				.andExpect(jsonPath("$.health[2].signals.length()").value(0))
				.andExpect(jsonPath("$.criteria.staleDays").value(14));
	}

	@Test
	void projectScopeOnlyCountsThatProject() throws Exception {
		mockMvc.perform(get(url()).param("projectId", apollo.getId().toString()).header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.projects").doesNotExist())
				.andExpect(jsonPath("$.tasks.total").value(7))
				.andExpect(jsonPath("$.tasks.open").value(5))
				.andExpect(jsonPath("$.health[*].name").value(contains("Apollo")))
				.andExpect(jsonPath("$.delivery.untrackedDone").value(0));
	}

	@Test
	void analyticsAreWorkspaceScopedAndValidated() throws Exception {
		mockMvc.perform(get(url()).header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken)).andExpect(status().isForbidden());
		mockMvc.perform(get(url()).param("projectId", foreignProject.getId().toString())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)).andExpect(status().isNotFound());
		mockMvc.perform(get(url()).param("from", today.toString()).param("to", today.minusDays(1).toString())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)).andExpect(status().isBadRequest());
		mockMvc.perform(get(url()).param("from", today.minusYears(2).toString())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)).andExpect(status().isBadRequest());
		mockMvc.perform(get(url())).andExpect(status().isUnauthorized());
	}

	@Test
	void completionTimeIsRecordedOnDoneAndClearedOnReopen() throws Exception {
		String statusUrl = "/api/tenants/" + tenant.getId() + "/projects/" + apollo.getId() + "/tasks/" + inProgressTask.getId() + "/status";
		mockMvc.perform(patch(statusUrl).header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}")).andExpect(status().isOk());
		Instant completedAt = taskRepository.findById(inProgressTask.getId()).orElseThrow().getCompletedAt();
		assertThat(completedAt).isCloseTo(Instant.now(), org.assertj.core.api.Assertions.within(1, ChronoUnit.MINUTES));

		mockMvc.perform(patch(statusUrl).header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"TODO\"}")).andExpect(status().isOk());
		assertThat(taskRepository.findById(inProgressTask.getId()).orElseThrow().getCompletedAt()).isNull();
	}
}
