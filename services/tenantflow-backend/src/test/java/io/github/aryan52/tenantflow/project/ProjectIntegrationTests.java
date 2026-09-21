package io.github.aryan52.tenantflow.project;

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
import io.github.aryan52.tenantflow.entity.Tenant;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.project.dto.CreateProjectRequest;
import io.github.aryan52.tenantflow.project.dto.UpdateProjectRequest;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import io.github.aryan52.tenantflow.repository.TenantRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_project_api;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
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
class ProjectIntegrationTests {

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
	private JwtService jwtService;

	private User adminUser;
	private User memberUser;
	private User otherUser;
	private Tenant tenant;
	private Tenant otherTenant;
	private String adminToken;
	private String memberToken;
	private String otherToken;

	@BeforeEach
	void setup() {
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
	}

	@Test
	void adminCanCreateProject() throws Exception {
		CreateProjectRequest request = new CreateProjectRequest("My Project", "Description");

		mockMvc.perform(post("/api/tenants/" + tenant.getId() + "/projects")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("My Project"));

		assertThat(projectRepository.countByTenant_Id(tenant.getId())).isEqualTo(1);
	}

	@Test
	void memberCannotCreateProject() throws Exception {
		CreateProjectRequest request = new CreateProjectRequest("My Project", "Description");

		mockMvc.perform(post("/api/tenants/" + tenant.getId() + "/projects")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isForbidden());
	}

	@Test
	void crossTenantAccessBlocked() throws Exception {
		CreateProjectRequest request = new CreateProjectRequest("My Project", "Description");

		mockMvc.perform(post("/api/tenants/" + tenant.getId() + "/projects")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminCanUpdateProject() throws Exception {
		Project project = projectRepository.save(Project.builder().tenant(tenant).name("Old").build());

		UpdateProjectRequest request = new UpdateProjectRequest("New", "Desc");

		mockMvc.perform(put("/api/tenants/" + tenant.getId() + "/projects/" + project.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("New"));
	}

	@Test
	void memberCanViewProjects() throws Exception {
		Project project = projectRepository.save(Project.builder().tenant(tenant).name("P1").build());

		mockMvc.perform(get("/api/tenants/" + tenant.getId() + "/projects")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("P1"));

		mockMvc.perform(get("/api/tenants/" + tenant.getId() + "/projects/" + project.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("P1"));
	}

	@Test
	void adminCanDeleteProject() throws Exception {
		Project project = projectRepository.save(Project.builder().tenant(tenant).name("P1").build());

		mockMvc.perform(delete("/api/tenants/" + tenant.getId() + "/projects/" + project.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isNoContent());

		assertThat(projectRepository.findById(project.getId())).isEmpty();
	}
}
