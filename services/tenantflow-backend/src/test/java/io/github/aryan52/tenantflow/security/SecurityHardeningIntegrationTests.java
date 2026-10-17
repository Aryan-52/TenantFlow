package io.github.aryan52.tenantflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.aryan52.tenantflow.audit.AuditAction;
import io.github.aryan52.tenantflow.audit.AuditEvent;
import io.github.aryan52.tenantflow.audit.AuditEventRepository;
import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.Project;
import io.github.aryan52.tenantflow.entity.Role;
import io.github.aryan52.tenantflow.entity.Task;
import io.github.aryan52.tenantflow.entity.TaskPriority;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import io.github.aryan52.tenantflow.entity.Tenant;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.PasswordResetTokenRepository;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import io.github.aryan52.tenantflow.repository.TaskRepository;
import io.github.aryan52.tenantflow.repository.TenantRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** Error mapping, correlation ids, security headers, CORS, optimistic locking and secret hygiene. */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_security_hardening;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-hardening-secret-test-hardening-secret-test-hardening",
		"tenantflow.jwt.expiration-seconds=3600",
		"tenantflow.cors.allowed-origins=http://localhost:5173"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class SecurityHardeningIntegrationTests {

	private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

	@Autowired private MockMvc mockMvc;
	@Autowired private UserRepository userRepository;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private MembershipRepository membershipRepository;
	@Autowired private ProjectRepository projectRepository;
	@Autowired private TaskRepository taskRepository;
	@Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
	@Autowired private AuditEventRepository auditEventRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private JwtService jwtService;

	private User user;
	private Tenant tenant;
	private Project project;
	private String token;

	@BeforeEach
	void setup() {
		auditEventRepository.deleteAll();
		passwordResetTokenRepository.deleteAll();
		taskRepository.deleteAll();
		projectRepository.deleteAll();
		membershipRepository.deleteAll();
		tenantRepository.deleteAll();
		userRepository.deleteAll();

		user = userRepository.save(User.builder().email("alice@example.com").name("Alice")
				.passwordHash(passwordEncoder.encode("Correct1!")).build());
		tenant = tenantRepository.save(Tenant.builder().name("T").slug("t").build());
		membershipRepository.save(Membership.builder().user(user).tenant(tenant).role(Role.OWNER).build());
		project = projectRepository.save(Project.builder().tenant(tenant).name("P").build());
		token = jwtService.generateToken(user);
	}

	private String bearer() {
		return "Bearer " + token;
	}

	// ---------------------------------------------------------------- errors

	@Test
	void unknownApiUrlIs404NotA500() throws Exception {
		mockMvc.perform(get("/api/does-not-exist").header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Resource not found"))
				.andExpect(jsonPath("$.requestId").exists());
	}

	@Test
	void wrongMethodIs405() throws Exception {
		mockMvc.perform(delete("/api/tenants").header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(status().isMethodNotAllowed());
	}

	@Test
	void unauthenticatedApiAccessIs401WithoutDetails() throws Exception {
		mockMvc.perform(get("/api/tenants"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Authentication required"));
	}

	// ---------------------------------------------------------------- correlation ids

	@Test
	void requestIdIsGeneratedEchoedAndSanitised() throws Exception {
		mockMvc.perform(get("/api/tenants").header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(header().string("X-Request-Id", matchesPattern(UUID_PATTERN)));

		mockMvc.perform(get("/api/tenants").header(HttpHeaders.AUTHORIZATION, bearer())
						.header("X-Request-Id", "edge-trace-12345"))
				.andExpect(header().string("X-Request-Id", "edge-trace-12345"));

		// Log/header injection attempt: replaced, never echoed.
		mockMvc.perform(get("/api/tenants").header(HttpHeaders.AUTHORIZATION, bearer())
						.header("X-Request-Id", "bad id\r\nSet-Cookie: x=y"))
				.andExpect(header().string("X-Request-Id", matchesPattern(UUID_PATTERN)));
	}

	// ---------------------------------------------------------------- headers and CORS

	@Test
	void apiResponsesCarrySecurityHeaders() throws Exception {
		mockMvc.perform(get("/api/tenants").header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
				.andExpect(header().string("Referrer-Policy", "no-referrer"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("X-Frame-Options", "DENY"))
				.andExpect(header().exists("Permissions-Policy"));
	}

	@Test
	void corsAllowsOnlyConfiguredOriginsAndHeaders() throws Exception {
		mockMvc.perform(options("/api/tenants")
						.header(HttpHeaders.ORIGIN, "http://localhost:5173")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,x-request-id"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));

		mockMvc.perform(options("/api/tenants")
						.header(HttpHeaders.ORIGIN, "https://evil.example")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isForbidden());

		mockMvc.perform(options("/api/tenants")
						.header(HttpHeaders.ORIGIN, "http://localhost:5173")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-not-allowed"))
				.andExpect(status().isForbidden());
	}

	// ---------------------------------------------------------------- optimistic locking

	@Test
	void staleTaskVersionIsRejectedAndCurrentVersionAccepted() throws Exception {
		Task task = taskRepository.save(Task.builder().project(project).title("T").status(TaskStatus.TODO).priority(TaskPriority.LOW).build());
		String url = "/api/tenants/" + tenant.getId() + "/projects/" + project.getId() + "/tasks/" + task.getId();
		long version = taskRepository.findById(task.getId()).orElseThrow().getVersion();

		// Someone else edits first.
		mockMvc.perform(put(url).header(HttpHeaders.AUTHORIZATION, bearer()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Theirs\",\"status\":\"TODO\",\"priority\":\"LOW\",\"version\":" + version + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(version + 1));

		// A second edit based on the old version must not overwrite theirs.
		mockMvc.perform(put(url).header(HttpHeaders.AUTHORIZATION, bearer()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Mine\",\"status\":\"DONE\",\"priority\":\"LOW\",\"version\":" + version + "}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("This task was changed by someone else. Reload it and try again."));
		assertThat(taskRepository.findById(task.getId()).orElseThrow().getTitle()).isEqualTo("Theirs");

		// Clients that do not send a version keep working (last write wins).
		mockMvc.perform(put(url).header(HttpHeaders.AUTHORIZATION, bearer()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Unversioned\",\"status\":\"TODO\",\"priority\":\"LOW\"}"))
				.andExpect(status().isOk());
	}

	@Test
	void staleProjectVersionIsRejected() throws Exception {
		String url = "/api/tenants/" + tenant.getId() + "/projects/" + project.getId();
		long version = projectRepository.findById(project.getId()).orElseThrow().getVersion();
		mockMvc.perform(put(url).header(HttpHeaders.AUTHORIZATION, bearer()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Renamed\",\"version\":" + version + "}"))
				.andExpect(status().isOk());
		mockMvc.perform(put(url).header(HttpHeaders.AUTHORIZATION, bearer()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Stale\",\"version\":" + version + "}"))
				.andExpect(status().isConflict());
		assertThat(projectRepository.findById(project.getId()).orElseThrow().getName()).isEqualTo("Renamed");
	}

	// ---------------------------------------------------------------- auditing and secrets

	@Test
	void loginOutcomesAreAuditedWithoutSecrets() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"alice@example.com\",\"password\":\"Wrong1!x\"}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"nobody@example.com\",\"password\":\"Wrong1!x\"}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"alice@example.com\",\"password\":\"Correct1!\"}"))
				.andExpect(status().isOk());

		var failed = auditEventRepository.findByAction(AuditAction.AUTH_LOGIN_FAILED);
		assertThat(failed).hasSize(2);
		assertThat(failed).extracting(AuditEvent::getDetails).containsExactlyInAnyOrder("bad credentials", "unknown account");
		assertThat(auditEventRepository.findByAction(AuditAction.AUTH_LOGIN_SUCCEEDED)).hasSize(1);
		assertThat(auditEventRepository.findAll()).allSatisfy(e ->
				assertThat(String.valueOf(e.getDetails())).doesNotContain("Correct1!").doesNotContain("Wrong1!x"));
	}

	@Test
	void passwordResetLinkIsNeverLoggedByDefault(CapturedOutput output) throws Exception {
		mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"alice@example.com\"}"))
				.andExpect(status().isOk());

		assertThat(output.getAll()).contains("a***@example.com");
		assertThat(output.getAll()).doesNotContain("reset-password?token=");
		assertThat(output.getAll()).doesNotContain("alice@example.com");
		assertThat(auditEventRepository.findByAction(AuditAction.AUTH_PASSWORD_RESET_REQUESTED)).hasSize(1);
	}

	@Test
	void workspaceUpdateAndProjectDeletionAreAudited() throws Exception {
		mockMvc.perform(put("/api/tenants/" + tenant.getId()).header(HttpHeaders.AUTHORIZATION, bearer())
						.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Renamed workspace\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(delete("/api/tenants/" + tenant.getId() + "/projects/" + project.getId())
						.header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(status().isNoContent());

		assertThat(auditEventRepository.findByAction(AuditAction.WORKSPACE_UPDATED)).singleElement()
				.satisfies(e -> assertThat(e.getActorId()).isEqualTo(user.getId()));
		assertThat(auditEventRepository.findByAction(AuditAction.PROJECT_DELETED)).singleElement()
				.satisfies(e -> {
					assertThat(e.getTenantId()).isEqualTo(tenant.getId());
					assertThat(e.getTargetId()).isEqualTo(project.getId());
					assertThat(e.getRequestId()).matches(UUID_PATTERN);
				});
		assertThat(auditEventRepository.findAll()).extracting(AuditEvent::getIpAddress).doesNotContainNull();
	}

	@Test
	void errorBodiesNeverContainStackTracesOrSql() throws Exception {
		mockMvc.perform(get("/api/tenants/not-a-uuid").header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(not(matchesPattern("(?s).*(Exception|at io\\.|SELECT|select ).*"))));
	}
}
