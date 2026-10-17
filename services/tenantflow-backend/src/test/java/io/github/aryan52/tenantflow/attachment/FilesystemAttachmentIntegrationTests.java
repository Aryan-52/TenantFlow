package io.github.aryan52.tenantflow.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import org.springframework.http.HttpMethod;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.aryan52.tenantflow.activity.ActivityEventRepository;
import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.Project;
import io.github.aryan52.tenantflow.entity.Role;
import io.github.aryan52.tenantflow.entity.Tenant;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.notification.NotificationRepository;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import io.github.aryan52.tenantflow.repository.TaskRepository;
import io.github.aryan52.tenantflow.repository.TenantRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.security.JwtService;
import io.github.aryan52.tenantflow.storage.FilesystemObjectStorage;
import io.github.aryan52.tenantflow.storage.ObjectStorage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** The local-development default: real files on disk, metadata in the database. */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_fs_attachments;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-fs-attachment-secret-test-fs-attachment-secret-x"
})
@AutoConfigureMockMvc
class FilesystemAttachmentIntegrationTests {

	/** A real 1x1 PNG. */
	private static final byte[] PNG = java.util.Base64.getDecoder().decode(
			"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

	@TempDir
	static Path storageRoot;

	@DynamicPropertySource
	static void storage(DynamicPropertyRegistry registry) {
		registry.add("tenantflow.storage.type", () -> "filesystem");
		registry.add("tenantflow.storage.directory", () -> storageRoot.toString());
	}

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
	private Project project;
	private Project foreignProject;
	private Tenant foreignTenant;
	private String memberToken;
	private String colleagueToken;
	private String adminToken;
	private String foreignToken;

	@BeforeEach
	void setup() {
		attachmentRepository.deleteAll();
		notificationRepository.deleteAll();
		activityEventRepository.deleteAll();
		taskRepository.deleteAll();
		projectRepository.deleteAll();
		membershipRepository.deleteAll();
		tenantRepository.deleteAll();
		userRepository.deleteAll();
		User member = userRepository.save(User.builder().email("m@fs.test").name("Member").passwordHash("h").build());
		User foreign = userRepository.save(User.builder().email("f@fs.test").name("Foreign").passwordHash("h").build());
		User colleague = userRepository.save(User.builder().email("c@fs.test").name("Colleague").passwordHash("h").build());
		User admin = userRepository.save(User.builder().email("a@fs.test").name("Admin").passwordHash("h").build());
		tenant = tenantRepository.save(Tenant.builder().name("T").slug("t-fs").build());
		foreignTenant = tenantRepository.save(Tenant.builder().name("F").slug("f-fs").build());
		membershipRepository.save(Membership.builder().user(member).tenant(tenant).role(Role.MEMBER).build());
		membershipRepository.save(Membership.builder().user(foreign).tenant(foreignTenant).role(Role.OWNER).build());
		membershipRepository.save(Membership.builder().user(colleague).tenant(tenant).role(Role.MEMBER).build());
		membershipRepository.save(Membership.builder().user(admin).tenant(tenant).role(Role.ADMIN).build());
		colleagueToken = jwtService.generateToken(colleague);
		adminToken = jwtService.generateToken(admin);
		project = projectRepository.save(Project.builder().tenant(tenant).name("Apollo").build());
		foreignProject = projectRepository.save(Project.builder().tenant(foreignTenant).name("Secret").build());
		memberToken = jwtService.generateToken(member);
		foreignToken = jwtService.generateToken(foreign);
	}

	private String base(Tenant t, Project p) {
		return "/api/tenants/" + t.getId() + "/projects/" + p.getId() + "/attachments";
	}

	private String upload(String filename, byte[] bytes) throws Exception {
		String body = mockMvc.perform(multipart(base(tenant, project)).file(new MockMultipartFile("file", filename, "application/octet-stream", bytes))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private long filesOnDisk() throws IOException {
		try (Stream<Path> files = Files.walk(storageRoot)) {
			return files.filter(Files::isRegularFile).count();
		}
	}

	@Test
	void uploadStoresBytesOnDiskAndDownloadReturnsThemSafely() throws Exception {
		assertThat(objectStorage).isInstanceOf(FilesystemObjectStorage.class);
		long before = filesOnDisk();
		String id = upload("pixel.png", PNG);
		assertThat(filesOnDisk()).isEqualTo(before + 1);

		mockMvc.perform(get(base(tenant, project) + "/" + id + "/download").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isOk())
				.andExpect(content().bytes(PNG))
				.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.startsWith("attachment")));

		// A new storage instance over the same directory (an application restart) still has it.
		Attachment saved = attachmentRepository.findById(java.util.UUID.fromString(id)).orElseThrow();
		try (InputStream in = new FilesystemObjectStorage(storageRoot).get(saved.getStorageKey())) {
			assertThat(in.readAllBytes()).isEqualTo(PNG);
		}
		// The storage path never leaks into the API.
		mockMvc.perform(get(base(tenant, project)).header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(jsonPath("$.items[0].storageKey").doesNotExist());
	}

	@Test
	void aMislabelledImageIsStoredAsWhatItReallyIs() throws Exception {
		byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
		String id = upload("screenshot.png", jpeg);
		mockMvc.perform(get(base(tenant, project) + "/" + id + "/download").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"));
	}

	@Test
	void aFileWhoseBytesWereLostIsReportedAccurately() throws Exception {
		String id = upload("pixel.png", PNG);
		Attachment saved = attachmentRepository.findById(java.util.UUID.fromString(id)).orElseThrow();
		objectStorage.delete(saved.getStorageKey());

		mockMvc.perform(get(base(tenant, project) + "/" + id + "/download").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("This file's contents are no longer available in storage. The file record and its history are kept: upload the original file again to restore it, or delete the entry."));
		// The record and its history are kept until someone decides.
		assertThat(attachmentRepository.count()).isEqualTo(1);
		// ...and the stale entry can still be removed.
		mockMvc.perform(delete(base(tenant, project) + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isNoContent());
		assertThat(attachmentRepository.count()).isZero();
	}

	@Test
	void filesInAnotherWorkspaceCannotBeListedDownloadedOrDeleted() throws Exception {
		String id = upload("pixel.png", PNG);
		mockMvc.perform(get(base(tenant, project) + "/" + id + "/download").header(HttpHeaders.AUTHORIZATION, "Bearer " + foreignToken))
				.andExpect(status().isForbidden());
		// Their own workspace id in the path, our file id: not found, nothing revealed.
		mockMvc.perform(get(base(foreignTenant, foreignProject) + "/" + id + "/download").header(HttpHeaders.AUTHORIZATION, "Bearer " + foreignToken))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete(base(foreignTenant, foreignProject) + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + foreignToken))
				.andExpect(status().isNotFound());
		assertThat(attachmentRepository.count()).isEqualTo(1);
	}

	/** A minimal, valid single-page PDF. */
	private static final byte[] PDF = ("%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj "
			+ "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n")
			.getBytes(java.nio.charset.StandardCharsets.US_ASCII);

	private org.springframework.test.web.servlet.ResultActions restore(String token, String id, String filename, byte[] bytes) throws Exception {
		return mockMvc.perform(multipart(HttpMethod.PUT, base(tenant, project) + "/" + id + "/content")
				.file(new MockMultipartFile("file", filename, "application/octet-stream", bytes))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	@Test
	void aPdfIsStoredAndDownloadedByteForByteWithItsType() throws Exception {
		String id = upload("NPTEL Exam Fee Receipt.pdf", PDF);
		mockMvc.perform(get(base(tenant, project) + "/" + id + "/download").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isOk())
				.andExpect(content().bytes(PDF))
				.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
				.andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, PDF.length));
		Attachment saved = attachmentRepository.findById(java.util.UUID.fromString(id)).orElseThrow();
		// Upload, preview and download all use the key stored with the record.
		assertThat(saved.getStorageKey()).isEqualTo("tenants/" + tenant.getId() + "/projects/" + project.getId() + "/" + id);
		assertThat(Files.exists(storageRoot.resolve(saved.getStorageKey()))).isTrue();
	}

	@Test
	void missingContentCanBeRestoredIntoTheSameRecordByTheUploaderOnly() throws Exception {
		String id = upload("receipt.pdf", PDF);
		Attachment saved = attachmentRepository.findById(java.util.UUID.fromString(id)).orElseThrow();

		// While the bytes exist, nothing can be "restored" over them.
		restore(memberToken, id, "receipt.pdf", PDF).andExpect(status().isConflict());

		objectStorage.delete(saved.getStorageKey());
		restore(colleagueToken, id, "receipt.pdf", PDF).andExpect(status().isForbidden());
		restore(memberToken, id, "pixel.png", PNG).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Choose a file of the same type as the original (PDF document)."));
		mockMvc.perform(multipart(HttpMethod.PUT, base(foreignTenant, foreignProject) + "/" + id + "/content")
						.file(new MockMultipartFile("file", "receipt.pdf", "application/pdf", PDF))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + foreignToken))
				.andExpect(status().isNotFound());

		restore(memberToken, id, "receipt.pdf", PDF).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
		mockMvc.perform(get(base(tenant, project) + "/" + id + "/download").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isOk())
				.andExpect(content().bytes(PDF));
		assertThat(activityEventRepository.findAll()).anyMatch(e -> e.getSummary().equals("restored the original contents of \"receipt.pdf\""));
	}

	@Test
	void anAdminRestoringDifferentContentIsRecordedAsAReplacement() throws Exception {
		String id = upload("plan.pdf", PDF);
		Attachment saved = attachmentRepository.findById(java.util.UUID.fromString(id)).orElseThrow();
		objectStorage.delete(saved.getStorageKey());
		byte[] other = new String(PDF, java.nio.charset.StandardCharsets.US_ASCII).replace("200 200", "300 300")
				.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		restore(adminToken, id, "plan.pdf", other).andExpect(status().isOk());
		assertThat(activityEventRepository.findAll()).anyMatch(e -> e.getSummary().equals("uploaded replacement contents for \"plan.pdf\""));
		assertThat(attachmentRepository.findById(saved.getId()).orElseThrow().getOriginalFilename()).isEqualTo("plan.pdf");
	}
}
