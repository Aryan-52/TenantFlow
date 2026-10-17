package io.github.aryan52.tenantflow.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.aryan52.tenantflow.attachment.Attachment;
import io.github.aryan52.tenantflow.attachment.AttachmentRepository;
import io.github.aryan52.tenantflow.automation.AutomationRun;
import io.github.aryan52.tenantflow.entity.Task;
import io.github.aryan52.tenantflow.entity.TaskPriority;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import io.github.aryan52.tenantflow.notification.Notification;
import io.github.aryan52.tenantflow.preferences.NotificationPreference;
import io.github.aryan52.tenantflow.preferences.ReminderService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_collab;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-collab-secret-test-collab-secret-test-collab-secret-x"
})
@AutoConfigureMockMvc
class CollaborationIntegrationTests extends WorkspaceFixture {

	private static final LocalDate TODAY = LocalDate.now();

	@Autowired private ReminderService reminderService;
	@Autowired private AttachmentRepository attachmentRepository;

	@BeforeEach
	void clearFiles() {
		attachmentRepository.deleteAll();
	}

	private List<Notification> notificationsOf(io.github.aryan52.tenantflow.entity.User u, String type) {
		return notificationRepository.findAll().stream()
				.filter(n -> n.getRecipientId().equals(u.getId()) && n.getType().equals(type)).toList();
	}

	// ---- Reminders ---------------------------------------------------------------------------

	@Test
	void remindersAreSentOncePerTaskAndDueDateAndRespectPreferences() {
		task(apollo, "Due tomorrow", TaskStatus.TODO, ava, TODAY.plusDays(1));
		task(apollo, "Late", TaskStatus.TODO, ava, TODAY.minusDays(3));
		task(apollo, "Next month", TaskStatus.TODO, ava, TODAY.plusDays(30));
		task(apollo, "Done late", TaskStatus.DONE, ava, TODAY.minusDays(3));
		task(apollo, "Max late", TaskStatus.TODO, max, TODAY.minusDays(1));
		preferenceRepository.save(NotificationPreference.builder().userId(max.getId()).overdueEnabled(false).build());
		// Oz is assigned in Acme but is not a member there: never reminded.
		task(apollo, "Outsider", TaskStatus.TODO, oz, TODAY.minusDays(1));

		reminderService.runOnce();
		reminderService.runOnce(); // a second run (or a restart) adds nothing

		assertThat(notificationsOf(ava, "REMINDER_DUE_SOON")).singleElement()
				.satisfies(n -> assertThat(n.getTitle()).isEqualTo("Due tomorrow: Due tomorrow"));
		assertThat(notificationsOf(ava, "REMINDER_OVERDUE")).singleElement()
				.satisfies(n -> assertThat(n.getTitle()).isEqualTo("Overdue: Late"));
		assertThat(notificationsOf(max, "REMINDER_OVERDUE")).isEmpty();
		assertThat(notificationsOf(oz, "REMINDER_OVERDUE")).isEmpty();
	}

	@Test
	void preferencesValidateTheTimeZoneAndBelongToTheCaller() throws Exception {
		putAs(avaToken, "/api/users/me/preferences",
				"{\"dueSoonEnabled\":true,\"dueSoonDays\":3,\"overdueEnabled\":false,\"mentionsEnabled\":true,\"timeZone\":\"Asia/Kolkata\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.effectiveTimeZone").value("Asia/Kolkata"))
				.andExpect(jsonPath("$.dueSoonDays").value(3));
		putAs(avaToken, "/api/users/me/preferences",
				"{\"dueSoonEnabled\":true,\"dueSoonDays\":3,\"overdueEnabled\":false,\"mentionsEnabled\":true,\"timeZone\":\"Mars/Base\"}")
				.andExpect(status().isBadRequest());
		getAs(maxToken, "/api/users/me/preferences").andExpect(jsonPath("$.dueSoonDays").value(1)); // defaults, not Ava's
	}

	// ---- Automations --------------------------------------------------------------------------

	@Test
	void automationsAreOffByDefaultAdminSwitchedAndRunOnce() throws Exception {
		String rules = base(acme) + "/automations";
		getAs(maxToken, rules).andExpect(jsonPath("$[?(@.type=='URGENT_NOTIFY_OWNER')].enabled", hasItem(false)));
		putAs(maxToken, rules + "/URGENT_NOTIFY_OWNER", "{\"enabled\":true}").andExpect(status().isForbidden());
		putAs(ozToken, rules + "/URGENT_NOTIFY_OWNER", "{\"enabled\":true}").andExpect(status().isForbidden());
		putAs(adaToken, rules + "/NOT_A_RULE", "{\"enabled\":true}").andExpect(status().isBadRequest());

		// Off: an urgent task does nothing.
		postAs(maxToken, base(acme) + "/projects/" + apollo.getId() + "/tasks",
				"{\"title\":\"Fire\",\"status\":\"TODO\",\"priority\":\"URGENT\"}").andExpect(status().isCreated());
		assertThat(notificationsOf(ada, "AUTOMATION_URGENT")).isEmpty();

		putAs(adaToken, rules + "/URGENT_NOTIFY_OWNER", "{\"enabled\":true}").andExpect(status().isOk());
		putAs(adaToken, rules + "/UNBLOCKED_NOTIFY_ASSIGNEE", "{\"enabled\":true}").andExpect(status().isOk());
		putAs(adaToken, rules + "/OVERDUE_NOTIFY_OWNER", "{\"enabled\":true}").andExpect(status().isOk());

		postAs(maxToken, base(acme) + "/projects/" + apollo.getId() + "/tasks",
				"{\"title\":\"Flood\",\"status\":\"TODO\",\"priority\":\"URGENT\"}").andExpect(status().isCreated());
		assertThat(notificationsOf(ada, "AUTOMATION_URGENT")).singleElement()
				.satisfies(n -> assertThat(n.getTitle()).isEqualTo("Urgent task: Flood"));

		// Finishing a blocker tells the blocked task's assignee it can start.
		Task blocker = task(apollo, "Blocker", TaskStatus.TODO, max, null);
		Task waiting = task(apollo, "Waiting", TaskStatus.TODO, ava, null);
		dependencyRepository.save(TaskDependency.builder().tenantId(acme.getId()).blockingTaskId(blocker.getId())
				.blockedTaskId(waiting.getId()).build());
		setStatus(maxToken, blocker, TaskStatus.DONE).andExpect(status().isOk());
		assertThat(notificationsOf(ava, "AUTOMATION_UNBLOCKED")).singleElement()
				.satisfies(n -> assertThat(n.getTitle()).isEqualTo("Ready to start: Waiting"));

		// Scheduled overdue rule: once per task and due date, never for the other workspace.
		task(apollo, "Overdue", TaskStatus.TODO, ava, TODAY.minusDays(2));
		task(zeus, "Zeus overdue", TaskStatus.TODO, oz, TODAY.minusDays(2));
		reminderService.runOnce();
		reminderService.runOnce();
		assertThat(notificationsOf(ada, "AUTOMATION_OVERDUE")).singleElement();
		assertThat(notificationsOf(oz, "AUTOMATION_OVERDUE")).isEmpty(); // rule is off in "Other"

		getAs(maxToken, rules + "/runs")
				.andExpect(jsonPath("$.items[*].type", hasItem("OVERDUE_NOTIFY_OWNER")))
				.andExpect(jsonPath("$.items[*].outcome", not(hasItem("FAILED"))));
		assertThat(runRepository.findAll()).allMatch(r -> r.getTenantId().equals(acme.getId()));
		assertThat(runRepository.findAll().stream().filter(r -> r.getRuleType().name().equals("OVERDUE_NOTIFY_OWNER")))
				.extracting(AutomationRun::getOutcome).containsOnly(AutomationRun.Outcome.SUCCESS).hasSize(1);
		getAs(ozToken, base(other) + "/automations/runs").andExpect(jsonPath("$.items", hasSize(0)));
	}

	// ---- Mentions & inbox --------------------------------------------------------------------

	@Test
	void mentionsNotifyMembersNamedInTheTextOnceAndInboxFiltersByCategory() throws Exception {
		Task t = task(apollo, "Review", TaskStatus.TODO, ava, null);
		String comments = taskUrl(t) + "/comments";
		String body = "{\"body\":\"@Max and @Oz please look\",\"mentionedUserIds\":[\"" + max.getId() + "\",\"" + oz.getId()
				+ "\",\"" + ada.getId() + "\"]}";
		String created = postAs(adaToken, comments, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String commentId = com.jayway.jsonpath.JsonPath.read(created, "$.id");

		assertThat(notificationsOf(max, "MENTION")).singleElement()
				.satisfies(n -> assertThat(n.getTitle()).isEqualTo("Ada mentioned you on Review"));
		assertThat(notificationsOf(oz, "MENTION")).isEmpty();  // not a member of this workspace
		assertThat(notificationsOf(ada, "MENTION")).isEmpty(); // the author; and Ada's name is not in the text
		// The assignee still hears about the comment the usual way.
		assertThat(notificationsOf(ava, "TASK_COMMENTED")).hasSize(1);

		// Editing the comment does not mention Max again.
		putAs(adaToken, comments + "/" + commentId, body).andExpect(status().isOk());
		assertThat(notificationsOf(max, "MENTION")).hasSize(1);

		getAs(maxToken, "/api/notifications?category=mentions").andExpect(jsonPath("$.items", hasSize(1)));
		getAs(maxToken, "/api/notifications?category=reminders").andExpect(jsonPath("$.items", hasSize(0)));
		getAs(maxToken, "/api/notifications?category=bogus").andExpect(status().isBadRequest());
	}

	// ---- Documents -----------------------------------------------------------------------------

	@Test
	void documentCenterListsSearchesAndFiltersOnlyThisWorkspacesFiles() throws Exception {
		Task t = task(apollo, "Spec", TaskStatus.TODO, ava, null);
		file(apollo.getId(), t.getId(), "design_v2.png", "image/png", 2000);
		file(apollo.getId(), null, "contract.pdf", "application/pdf", 9000);
		file(zeus.getId(), null, "zeus-secret.pdf", "application/pdf", 10);

		String docs = base(acme) + "/documents";
		getAs(maxToken, docs).andExpect(jsonPath("$.items", hasSize(2)))
				.andExpect(jsonPath("$.items[*].filename", not(hasItem("zeus-secret.pdf"))));
		getAs(maxToken, docs + "?type=pdf").andExpect(jsonPath("$.items[0].filename").value("contract.pdf"));
		getAs(maxToken, docs + "?q=V2").andExpect(jsonPath("$.items[0].taskTitle").value("Spec"))
				.andExpect(jsonPath("$.items[0].projectName").value("Apollo"));
		getAs(maxToken, docs + "?q=_v").andExpect(jsonPath("$.items", hasSize(1))); // "_" is literal, not a wildcard
		getAs(maxToken, docs + "?sort=largest").andExpect(jsonPath("$.items[0].filename").value("contract.pdf"));
		getAs(maxToken, docs + "?sort=random").andExpect(status().isBadRequest());
		getAs(ozToken, docs).andExpect(status().isForbidden());
		getAs(ozToken, base(other) + "/documents?projectId=" + apollo.getId()).andExpect(jsonPath("$.items", hasSize(0)));
	}

	private void file(java.util.UUID projectId, java.util.UUID taskId, String name, String type, long size) {
		java.util.UUID tenantId = projectId.equals(zeus.getId()) ? other.getId() : acme.getId();
		attachmentRepository.save(Attachment.builder().id(java.util.UUID.randomUUID()).tenantId(tenantId).projectId(projectId).taskId(taskId)
				.uploadedBy(ada.getId()).originalFilename(name).contentType(type).sizeBytes(size)
				.storageKey("k/" + java.util.UUID.randomUUID().toString().replace("-", "")).sha256("0".repeat(64)).build());
	}

	// ---- Reports -------------------------------------------------------------------------------

	@Test
	void reportsUseFilteredLiveDataAndExportSafeCsv() throws Exception {
		Task late = task(apollo, "=HYPERLINK(\"http://evil\")", TaskStatus.TODO, ava, TODAY.minusDays(4));
		late.setPriority(TaskPriority.HIGH);
		taskRepository.save(late);
		task(apollo, "On, track", TaskStatus.TODO, max, TODAY.minusDays(1));
		task(zeus, "Zeus late", TaskStatus.TODO, oz, TODAY.minusDays(9));

		String reports = base(acme) + "/reports";
		getAs(maxToken, reports + "/overdue-tasks")
				.andExpect(jsonPath("$.rows", hasSize(2)))
				.andExpect(jsonPath("$.columns[6].key").value("daysOverdue"))
				.andExpect(jsonPath("$.rows[0][6]").value("4"));
		getAs(maxToken, reports + "/overdue-tasks?assigneeId=" + max.getId()).andExpect(jsonPath("$.rows", hasSize(1)));
		getAs(maxToken, reports + "/overdue-tasks?assigneeId=" + oz.getId()).andExpect(status().isNotFound());
		getAs(maxToken, reports + "/nope").andExpect(status().isBadRequest());
		getAs(ozToken, reports + "/overdue-tasks").andExpect(status().isForbidden());

		getAs(maxToken, reports + "/overdue-tasks/csv")
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", containsString("attachment")))
				.andExpect(content().string(containsString("Task,Project,Assignee")))
				.andExpect(content().string(containsString("\"'=HYPERLINK(\"\"http://evil\"\")\"")))  // neutralised and quoted
				.andExpect(content().string(containsString("\"On, track\"")))
				.andExpect(content().string(not(containsString("Zeus late"))));

		getAs(maxToken, reports + "/project-status").andExpect(jsonPath("$.rows[0][0]").value("Apollo"));
		getAs(maxToken, reports + "/workload").andExpect(jsonPath("$.rows[*][0]", hasItem("Ava")));
		getAs(maxToken, reports + "/milestones").andExpect(jsonPath("$.rows", hasSize(0)));
		getAs(maxToken, reports + "/task-completion?from=" + TODAY.plusDays(1) + "&to=" + TODAY).andExpect(status().isBadRequest());
	}
}
