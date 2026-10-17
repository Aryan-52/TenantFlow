package io.github.aryan52.tenantflow.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import io.github.aryan52.tenantflow.storage.InMemoryObjectStorage;
import io.github.aryan52.tenantflow.storage.ObjectStorage;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_attachments;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-attachment-secret-test-attachment-secret-test-att",
		"tenantflow.storage.type=memory",
		"tenantflow.storage.max-file-size=2KB"
})
@AutoConfigureMockMvc
class AttachmentIntegrationTests {

	private static final byte[] PDF = "%PDF-1.7\nhello".getBytes(StandardCharsets.US_ASCII);

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectStorage objectStorage;
	@Autowired private AttachmentRepository attachmentRepository;
	@Autowired private ActivityEventRepository activityEventRepository;
	@Autowired private NotificationRepository notificationRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private MembershipRepository membershipRepository;
	@Autowired private ProjectRepository projectRepository;
	@Autowired private TaskRepository taskRepository;
	@Autowired private JwtService jwtService;

	private Tenant tenant;
	private Tenant otherTenant;
	private Project project;
	private Project otherProject;
	private Project foreignProject;
	private Task task;
	private String adminToken;
	private String memberToken;
	private String member2Token;
	private String foreignToken;

	private InMemoryObjectStorage storage() {
		return (InMemoryObjectStorage) objectStorage;
	}

	@BeforeEach
	void setup() {
		storage().clear();
		attachmentRepository.deleteAll();
		notificationRepository.deleteAll();
		activityEventRepository.deleteAll();
		taskRepository.deleteAll();
		projectRepository.deleteAll();
		membershipRepository.deleteAll();
		tenantRepository.deleteAll();
		userRepository.deleteAll();

		User admin = userRepository.save(User.builder().email("admin@example.com").name("Admin").passwordHash("h").build());
		User member = userRepository.save(User.builder().email("member@example.com").name("Member").passwordHash("h").build());
		User member2 = userRepository.save(User.builder().email("member2@example.com").name("Member2").passwordHash("h").build());
		User foreign = userRepository.save(User.builder().email("foreign@example.com").name("Foreign").passwordHash("h").build());
		tenant = tenantRepository.save(Tenant.builder().name("T").slug("t").build());
		otherTenant = tenantRepository.save(Tenant.builder().name("O").slug("o").build());
		membershipRepository.save(Membership.builder().user(admin).tenant(tenant).role(Role.ADMIN).build());
		membershipRepository.save(Membership.builder().user(member).tenant(tenant).role(Role.MEMBER).build());
		membershipRepository.save(Membership.builder().user(member2).tenant(tenant).role(Role.MEMBER).build());
		membershipRepository.save(Membership.builder().user(foreign).tenant(otherTenant).role(Role.OWNER).build());
		project = projectRepository.save(Project.builder().tenant(tenant).name("Apollo").build());
		otherProject = projectRepository.save(Project.builder().tenant(tenant).name("Gemini").build());
		foreignProject = projectRepository.save(Project.builder().tenant(otherTenant).name("Secret").build());
		task = taskRepository.save(Task.builder().project(project).title("Ship").status(TaskStatus.TODO).priority(TaskPriority.LOW).build());
		adminToken = jwtService.generateToken(admin);
		memberToken = jwtService.generateToken(member);
		member2Token = jwtService.generateToken(member2);
		foreignToken = jwtService.generateToken(foreign);
	}

	private String base(Tenant t, Project p) {
		return "/api/tenants/" + t.getId() + "/projects/" + p.getId() + "/attachments";
	}

	private ResultActions upload(String token, Tenant t, Project p, String taskId, String filename, String declaredType, byte[] bytes)
			throws Exception {
		var req = multipart(base(t, p) + (taskId == null ? "" : "?taskId=" + taskId))
				.file(new MockMultipartFile("file", filename, declaredType, bytes));
		return mockMvc.perform(req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private String uploadPdf(String token) throws Exception {
		String body = upload(token, tenant, project, task.getId().toString(), "spec.pdf", "application/pdf", PDF)
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	@Test
	void uploadListAndDownloadAsAMember() throws Exception {
		upload(memberToken, tenant, project, task.getId().toString(), "spec.pdf", "text/html", PDF)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.filename").value("spec.pdf"))
				// The client-declared type is ignored; the canonical type is derived server-side.
				.andExpect(jsonPath("$.contentType").value("application/pdf"))
				.andExpect(jsonPath("$.sizeBytes").value(PDF.length))
				.andExpect(jsonPath("$.taskId").value(task.getId().toString()))
				.andExpect(jsonPath("$.uploadedByName").value("Member"))
				.andExpect(jsonPath("$.storageKey").doesNotExist());
		upload(memberToken, tenant, project, null, "notes.txt", "text/plain", "plain".getBytes()).andExpect(status().isCreated());

		mockMvc.perform(get(base(tenant, project)).header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(jsonPath("$.totalElements").value(2));
		mockMvc.perform(get(base(tenant, project) + "?taskId=" + task.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(jsonPath("$.totalElements").value(1));

		assertThat(storage().keys()).allSatisfy(k -> assertThat(k).startsWith("tenants/" + tenant.getId() + "/projects/" + project.getId() + "/"));

		String id = attachmentRepository.findAll().stream().filter(a -> a.getOriginalFilename().equals("spec.pdf")).findFirst().orElseThrow().getId().toString();
		mockMvc.perform(get(base(tenant, project) + "/" + id + "/download").header(HttpHeaders.AUTHORIZATION, "Bearer " + member2Token))
				.andExpect(status().isOk())
				.andExpect(content().bytes(PDF))
				.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.startsWith("attachment;")))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("spec.pdf")))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}

	@Test
	void otherWorkspacesCannotUploadListDownloadOrDelete() throws Exception {
		String id = uploadPdf(memberToken);

		upload(foreignToken, tenant, project, null, "x.pdf", "application/pdf", PDF).andExpect(status().isForbidden());
		mockMvc.perform(get(base(tenant, project)).header(HttpHeaders.AUTHORIZATION, "Bearer " + foreignToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(base(tenant, project) + "/" + id + "/download").header(HttpHeaders.AUTHORIZATION, "Bearer " + foreignToken))
				.andExpect(status().isForbidden());
		// Pairing their own workspace with our attachment id finds nothing.
		mockMvc.perform(get(base(otherTenant, foreignProject) + "/" + id + "/download").header(HttpHeaders.AUTHORIZATION, "Bearer " + foreignToken))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete(base(otherTenant, foreignProject) + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + foreignToken))
				.andExpect(status().isNotFound());
		// Our own workspace with their project id, or our attachment via another project: 404.
		upload(memberToken, tenant, foreignProject, null, "x.pdf", "application/pdf", PDF).andExpect(status().isNotFound());
		mockMvc.perform(get(base(tenant, otherProject) + "/" + id + "/download").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isNotFound());
		// A task of another project cannot be targeted.
		upload(memberToken, tenant, otherProject, task.getId().toString(), "x.pdf", "application/pdf", PDF).andExpect(status().isNotFound());

		assertThat(attachmentRepository.count()).isEqualTo(1);
	}

	@Test
	void invalidFilesAreRejectedAndNothingIsStored() throws Exception {
		upload(memberToken, tenant, project, null, "run.exe", "application/octet-stream", "MZ...".getBytes()).andExpect(status().isBadRequest());
		upload(memberToken, tenant, project, null, "page.html", "text/html", "<script>".getBytes()).andExpect(status().isBadRequest());
		upload(memberToken, tenant, project, null, "fake.png", "image/png", PDF)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("This file is a PDF document, but its name ends in .png. Rename it to .pdf and upload it again."));
		upload(memberToken, tenant, project, null, "big.pdf", "application/pdf", new byte[3000])
				.andExpect(status().isBadRequest());
		upload(memberToken, tenant, project, null, "empty.pdf", "application/pdf", new byte[0]).andExpect(status().isBadRequest());

		assertThat(attachmentRepository.count()).isZero();
		assertThat(storage().keys()).isEmpty();
	}

	@Test
	void pathTraversalFilenamesAreNeutralised() throws Exception {
		upload(memberToken, tenant, project, null, "../../../etc/passwd.txt", "text/plain", "root".getBytes())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.filename").value("passwd.txt"));
		assertThat(storage().keys()).singleElement().satisfies(k -> assertThat(k).doesNotContain(".."));
	}

	@Test
	void onlyTheUploaderOrAnAdminCanDelete() throws Exception {
		String id = uploadPdf(memberToken);
		mockMvc.perform(delete(base(tenant, project) + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + member2Token))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete(base(tenant, project) + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isNoContent());
		assertThat(storage().keys()).isEmpty();

		String id2 = uploadPdf(memberToken);
		mockMvc.perform(delete(base(tenant, project) + "/" + id2).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isNoContent());
		assertThat(attachmentRepository.count()).isZero();
	}

	@Test
	void deletingATaskOrProjectRemovesItsFiles() throws Exception {
		uploadPdf(memberToken);
		upload(memberToken, tenant, project, null, "project.txt", "text/plain", "p".getBytes()).andExpect(status().isCreated());
		assertThat(storage().keys()).hasSize(2);

		mockMvc.perform(delete("/api/tenants/" + tenant.getId() + "/projects/" + project.getId() + "/tasks/" + task.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isNoContent());
		assertThat(attachmentRepository.count()).isEqualTo(1);
		assertThat(storage().keys()).hasSize(1);

		mockMvc.perform(delete("/api/tenants/" + tenant.getId() + "/projects/" + project.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isNoContent());
		assertThat(attachmentRepository.count()).isZero();
		assertThat(storage().keys()).isEmpty();
	}

	@Test
	void movingATaskTakesItsAttachmentsAlong() throws Exception {
		String id = uploadPdf(memberToken);
		mockMvc.perform(put("/api/tenants/" + tenant.getId() + "/projects/" + project.getId() + "/tasks/" + task.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Ship\",\"status\":\"TODO\",\"priority\":\"LOW\",\"projectId\":\"" + otherProject.getId() + "\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(get(base(tenant, otherProject) + "/" + id + "/download").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isOk());
		mockMvc.perform(get(base(tenant, project) + "?taskId=" + task.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(jsonPath("$.totalElements").value(0));
	}
}
