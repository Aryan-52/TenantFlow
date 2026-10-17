package io.github.aryan52.tenantflow.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Global search on real PostgreSQL (Flyway migrations incl. trigram indexes) with tenant isolation. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:postgresql://must-be-overridden-by-testcontainers.invalid:1/none",
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate",
		"tenantflow.jwt.secret=test-search-secret-test-search-secret-test-search-secret"
})
@AutoConfigureMockMvc
class SearchPostgresIntegrationTests {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired private MockMvc mockMvc;
	@Autowired private UserRepository userRepository;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private MembershipRepository membershipRepository;
	@Autowired private ProjectRepository projectRepository;
	@Autowired private TaskRepository taskRepository;
	@Autowired private JwtService jwtService;
	@Autowired private DataSource dataSource;

	private Membership aliceInBeta;
	private String aliceToken;
	private String bobToken;

	@BeforeEach
	void setup() {
		new JdbcTemplate(dataSource).execute("truncate attachments, notifications, activity_events, idempotency_keys, tasks, projects, memberships, tenants, users cascade");

		User alice = userRepository.save(User.builder().email("alice@example.com").name("Alice").passwordHash("h").build());
		User bob = userRepository.save(User.builder().email("bob@example.com").name("Bob").passwordHash("h").build());
		Tenant alpha = tenantRepository.save(Tenant.builder().name("Alpha Corp").slug("alpha").build());
		Tenant beta = tenantRepository.save(Tenant.builder().name("Beta Inc").slug("beta").build());
		Tenant secret = tenantRepository.save(Tenant.builder().name("Secret Ltd").slug("secret").build());
		membershipRepository.save(Membership.builder().user(alice).tenant(alpha).role(Role.OWNER).build());
		aliceInBeta = membershipRepository.save(Membership.builder().user(alice).tenant(beta).role(Role.MEMBER).build());
		membershipRepository.save(Membership.builder().user(bob).tenant(secret).role(Role.OWNER).build());

		Project apollo = projectRepository.save(Project.builder().tenant(alpha).name("Apollo launch").description("Moon mission").build());
		projectRepository.save(Project.builder().tenant(alpha).name("Big apple").build());
		Project gemini = projectRepository.save(Project.builder().tenant(beta).name("Gemini").description("Apollo follow-up").build());
		Project hidden = projectRepository.save(Project.builder().tenant(secret).name("Apollo secret plans").build());

		taskRepository.save(Task.builder().project(apollo).title("Fuel check").description("Check apollo fuel").status(TaskStatus.TODO).priority(TaskPriority.HIGH).build());
		taskRepository.save(Task.builder().project(gemini).title("Report 50% done").status(TaskStatus.DONE).priority(TaskPriority.LOW).build());
		taskRepository.save(Task.builder().project(gemini).title("Report 500 items").status(TaskStatus.TODO).priority(TaskPriority.LOW).build());
		taskRepository.save(Task.builder().project(hidden).title("Apollo hidden task").status(TaskStatus.TODO).priority(TaskPriority.LOW).build());

		aliceToken = jwtService.generateToken(alice);
		bobToken = jwtService.generateToken(bob);
	}

	private ResultActions search(String token, String query) throws Exception {
		return mockMvc.perform(get("/api/search").param("q", query).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	@Test
	void findsProjectsAndTasksAcrossTheUsersWorkspacesOnly() throws Exception {
		search(aliceToken, "APOLLO")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.projects.totalElements").value(2))
				// Prefix match ranks first; the description match second; Bob's workspace never appears.
				.andExpect(jsonPath("$.projects.items[0].name").value("Apollo launch"))
				.andExpect(jsonPath("$.projects.items[0].tenantName").value("Alpha Corp"))
				.andExpect(jsonPath("$.projects.items[1].name").value("Gemini"))
				.andExpect(jsonPath("$.tasks.totalElements").value(1))
				.andExpect(jsonPath("$.tasks.items[0].title").value("Fuel check"))
				.andExpect(jsonPath("$.tasks.items[0].projectName").value("Apollo launch"));

		search(bobToken, "apollo")
				.andExpect(jsonPath("$.projects.totalElements").value(1))
				.andExpect(jsonPath("$.projects.items[0].name").value("Apollo secret plans"))
				.andExpect(jsonPath("$.tasks.items[*].title", containsInAnyOrder("Apollo hidden task")));
	}

	@Test
	void leavingAWorkspaceRemovesItsResultsImmediately() throws Exception {
		search(aliceToken, "gemini").andExpect(jsonPath("$.projects.totalElements").value(1));
		membershipRepository.delete(aliceInBeta);
		search(aliceToken, "gemini").andExpect(jsonPath("$.projects.totalElements").value(0));
	}

	@Test
	void wildcardCharactersAreMatchedLiterally() throws Exception {
		search(aliceToken, "50%")
				.andExpect(jsonPath("$.tasks.totalElements").value(1))
				.andExpect(jsonPath("$.tasks.items[0].title").value("Report 50% done"));
		search(aliceToken, "_").andExpect(status().isBadRequest());
		search(aliceToken, "t_ check").andExpect(jsonPath("$.tasks.totalElements").value(0));
	}

	@Test
	void typeFilterValidationAndAuthentication() throws Exception {
		mockMvc.perform(get("/api/search").param("q", "apollo").param("type", "TASK").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
				.andExpect(jsonPath("$.projects").doesNotExist())
				.andExpect(jsonPath("$.tasks.totalElements").value(1));
		search(aliceToken, "a").andExpect(status().isBadRequest());
		search(aliceToken, "x".repeat(101)).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/search").param("q", "apollo").param("size", "500").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/search").param("q", "apollo")).andExpect(status().isUnauthorized());
	}

	@Test
	void trigramIndexesExist() {
		var indexes = new JdbcTemplate(dataSource).queryForList(
				"select indexname from pg_indexes where indexname in ('idx_projects_name_trgm', 'idx_tasks_title_trgm')", String.class);
		assertThat(indexes).containsExactlyInAnyOrder("idx_projects_name_trgm", "idx_tasks_title_trgm");
	}
}
