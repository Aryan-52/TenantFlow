package io.github.aryan52.tenantflow.tenant;

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
	private Tenant tenant;
	private String adminToken;

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
}
