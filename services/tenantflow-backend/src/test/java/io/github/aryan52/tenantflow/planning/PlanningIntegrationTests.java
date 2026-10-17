package io.github.aryan52.tenantflow.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.aryan52.tenantflow.entity.Task;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_planning;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-planning-secret-test-planning-secret-test-planning-x"
})
@AutoConfigureMockMvc
class PlanningIntegrationTests extends WorkspaceFixture {

	private static final LocalDate TODAY = LocalDate.now();

	// ---- Milestones ---------------------------------------------------------------------

	@Test
	void milestonesAreCreatedCompletedAndDeletedWithPermissionsAndHistory() throws Exception {
		String url = base(acme) + "/projects/" + apollo.getId() + "/milestones";
		String id = com.jayway.jsonpath.JsonPath.read(postAs(maxToken, url,
				"{\"name\":\"Beta\",\"targetDate\":\"" + TODAY.plusDays(10) + "\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andReturn().getResponse().getContentAsString(), "$.id");

		// Link a task, then complete the milestone: counts and activity follow.
		Task t = task(apollo, "Ship beta", TaskStatus.DONE, ava, null);
		t.setMilestoneId(java.util.UUID.fromString(id));
		taskRepository.save(t);
		putAs(maxToken, url + "/" + id, "{\"name\":\"Beta\",\"targetDate\":\"" + TODAY.plusDays(10) + "\",\"status\":\"DONE\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DONE"))
				.andExpect(jsonPath("$.completedAt").isNotEmpty())
				.andExpect(jsonPath("$.totalTasks").value(1))
				.andExpect(jsonPath("$.doneTasks").value(1));
		assertThat(activityRepository.findAll()).anyMatch(e -> e.getType().equals("MILESTONE_COMPLETED"));

		// Validation, workspace isolation and admin-only deletion.
		postAs(maxToken, url, "{\"name\":\" \",\"targetDate\":\"" + TODAY + "\"}").andExpect(status().isBadRequest());
		getAs(ozToken, url).andExpect(status().isForbidden());
		getAs(ozToken, base(other) + "/projects/" + apollo.getId() + "/milestones").andExpect(status().isNotFound());
		deleteAs(maxToken, url + "/" + id).andExpect(status().isForbidden());
		deleteAs(adaToken, url + "/" + id).andExpect(status().isNoContent());
		assertThat(taskRepository.findById(t.getId()).orElseThrow().getMilestoneId()).isNull(); // task kept, link cleared
	}

	@Test
	void aTaskCanOnlyBeLinkedToAMilestoneOfItsOwnProject() throws Exception {
		var other = projectRepository.save(io.github.aryan52.tenantflow.entity.Project.builder().tenant(acme).name("Gemini").build());
		Milestone m = milestoneRepository.save(Milestone.builder().tenantId(acme.getId()).projectId(other.getId())
				.name("M").targetDate(TODAY).build());
		postAs(maxToken, base(acme) + "/projects/" + apollo.getId() + "/tasks",
				"{\"title\":\"X\",\"status\":\"TODO\",\"priority\":\"LOW\",\"planning\":{\"milestoneId\":\"" + m.getId() + "\"}}")
				.andExpect(status().isNotFound());
	}

	// ---- Dependencies ----------------------------------------------------------------------

	@Test
	void dependenciesRejectSelfLinksCyclesAndOtherWorkspacesAndBlockCompletion() throws Exception {
		Task a = task(apollo, "Design", TaskStatus.TODO, ava, null);
		Task b = task(apollo, "Build", TaskStatus.TODO, ava, null);
		Task c = task(apollo, "Test", TaskStatus.TODO, ava, null);
		Task foreign = task(zeus, "Secret", TaskStatus.TODO, oz, null);
		String deps = base(acme) + "/tasks/";

		postAs(maxToken, deps + b.getId() + "/dependencies", "{\"blockingTaskId\":\"" + b.getId() + "\"}")
				.andExpect(status().isBadRequest());
		postAs(maxToken, deps + b.getId() + "/dependencies", "{\"blockingTaskId\":\"" + foreign.getId() + "\"}")
				.andExpect(status().isNotFound());
		postAs(maxToken, deps + b.getId() + "/dependencies", "{\"blockingTaskId\":\"" + a.getId() + "\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.blockedBy[0].title").value("Design"))
				.andExpect(jsonPath("$.openBlockers").value(1));
		// Retrying the same request is harmless.
		postAs(maxToken, deps + b.getId() + "/dependencies", "{\"blockingTaskId\":\"" + a.getId() + "\"}")
				.andExpect(jsonPath("$.blockedBy", hasSize(1)));
		postAs(maxToken, deps + c.getId() + "/dependencies", "{\"blockingTaskId\":\"" + b.getId() + "\"}").andExpect(status().isOk());
		// a -> b -> c; c blocking a would close the loop.
		postAs(maxToken, deps + a.getId() + "/dependencies", "{\"blockingTaskId\":\"" + c.getId() + "\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message", containsString("circular")));
		getAs(ozToken, base(other) + "/tasks/" + b.getId() + "/dependencies").andExpect(status().isNotFound());

		// Blocked tasks cannot be finished; the response says why. Lists show the blocker count.
		setStatus(avaToken, b, TaskStatus.DONE).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message", containsString("Design")));
		getAs(maxToken, base(acme) + "/projects/" + apollo.getId() + "/tasks")
				.andExpect(jsonPath("$[?(@.title=='Build')].openBlockers", hasItem(1)));
		setStatus(avaToken, a, TaskStatus.DONE).andExpect(status().isOk());
		setStatus(avaToken, b, TaskStatus.DONE).andExpect(status().isOk());

		// Deleting a task removes its links.
		deleteAs(adaToken, taskUrl(c)).andExpect(status().isNoContent());
		assertThat(dependencyRepository.findByBlockingTaskId(b.getId())).isEmpty();
	}

	// ---- Planning fields & recurrence --------------------------------------------------------

	@Test
	void planningFieldsAreKeptWhenAnUpdateOmitsThem() throws Exception {
		String created = postAs(maxToken, base(acme) + "/projects/" + apollo.getId() + "/tasks",
				"{\"title\":\"Plan\",\"status\":\"TODO\",\"priority\":\"LOW\",\"dueDate\":\"" + TODAY.plusDays(5)
						+ "\",\"planning\":{\"startDate\":\"" + TODAY + "\"}}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.startDate").value(TODAY.toString()))
				.andReturn().getResponse().getContentAsString();
		String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");
		String url = base(acme) + "/projects/" + apollo.getId() + "/tasks/" + id;
		// An older client (no "planning") keeps the start date.
		putAs(maxToken, url, "{\"title\":\"Plan\",\"status\":\"TODO\",\"priority\":\"HIGH\",\"dueDate\":\"" + TODAY.plusDays(5) + "\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.startDate").value(TODAY.toString()));
		// Due before start is rejected.
		putAs(maxToken, url, "{\"title\":\"Plan\",\"status\":\"TODO\",\"priority\":\"HIGH\",\"dueDate\":\"" + TODAY.minusDays(1) + "\"}")
				.andExpect(status().isBadRequest());
	}

	@Test
	void completingARecurringTaskCreatesTheNextOccurrenceExactlyOnce() throws Exception {
		LocalDate due = TODAY.plusDays(1);
		String created = postAs(maxToken, base(acme) + "/projects/" + apollo.getId() + "/tasks",
				"{\"title\":\"Standup notes\",\"status\":\"TODO\",\"priority\":\"LOW\",\"assigneeId\":\"" + ava.getId()
						+ "\",\"dueDate\":\"" + due + "\",\"planning\":{\"recurrence\":{\"frequency\":\"DAILY\",\"interval\":2,\"until\":\""
						+ due.plusDays(2) + "\"}}}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.recurrence.frequency").value("DAILY"))
				.andReturn().getResponse().getContentAsString();
		Task first = taskRepository.findById(java.util.UUID.fromString(com.jayway.jsonpath.JsonPath.read(created, "$.id"))).orElseThrow();

		setStatus(avaToken, first, TaskStatus.DONE).andExpect(status().isOk());
		setStatus(avaToken, first, TaskStatus.TODO).andExpect(status().isOk());
		setStatus(avaToken, first, TaskStatus.DONE).andExpect(status().isOk());
		var series = taskRepository.findAll().stream().filter(t -> first.getSeriesId().equals(t.getSeriesId())).toList();
		assertThat(series).hasSize(2);
		Task next = series.stream().filter(t -> !t.getId().equals(first.getId())).findFirst().orElseThrow();
		assertThat(next.getDueDate()).isEqualTo(due.plusDays(2));
		assertThat(next.getStatus()).isEqualTo(TaskStatus.TODO);
		assertThat(next.getAssignee().getId()).isEqualTo(ava.getId());

		// The rule ends on "until": completing the last occurrence creates nothing more.
		setStatus(avaToken, next, TaskStatus.DONE).andExpect(status().isOk());
		assertThat(taskRepository.findAll().stream().filter(t -> first.getSeriesId().equals(t.getSeriesId()))).hasSize(2);

		// A recurring task needs a due date.
		postAs(maxToken, base(acme) + "/projects/" + apollo.getId() + "/tasks",
				"{\"title\":\"X\",\"status\":\"TODO\",\"priority\":\"LOW\",\"planning\":{\"recurrence\":{\"frequency\":\"WEEKLY\"}}}")
				.andExpect(status().isBadRequest());
	}

	// ---- My work, calendar, portfolio, workload -------------------------------------------

	@Test
	void myWorkShowsOnlyMyOpenTasksInThisWorkspaceWithFocusAndRecentCompletions() throws Exception {
		Task mine = task(apollo, "Mine", TaskStatus.TODO, ava, TODAY);
		task(apollo, "Theirs", TaskStatus.TODO, max, TODAY);
		Task done = task(apollo, "Finished", TaskStatus.TODO, ava, null);
		setStatus(avaToken, done, TaskStatus.DONE).andExpect(status().isOk());

		putAs(avaToken, base(acme) + "/my-work/focus/" + mine.getId(), "").andExpect(status().isNoContent());
		putAs(avaToken, base(acme) + "/my-work/focus/" + mine.getId(), "").andExpect(status().isNoContent()); // idempotent
		getAs(avaToken, base(acme) + "/my-work")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.open", hasSize(1)))
				.andExpect(jsonPath("$.open[0].title").value("Mine"))
				.andExpect(jsonPath("$.focusTaskIds[0]").value(mine.getId().toString()))
				.andExpect(jsonPath("$.completed[0].title").value("Finished"));
		putAs(ozToken, base(acme) + "/my-work/focus/" + mine.getId(), "").andExpect(status().isForbidden());
		putAs(ozToken, base(other) + "/my-work/focus/" + mine.getId(), "").andExpect(status().isNotFound());
	}

	@Test
	void calendarSeparatesMyScheduleFromTheWorkspaceAndIncludesMilestones() throws Exception {
		task(apollo, "Ava due", TaskStatus.TODO, ava, TODAY.plusDays(2));
		task(apollo, "Max due", TaskStatus.TODO, max, TODAY.plusDays(3));
		task(apollo, "Far away", TaskStatus.TODO, ava, TODAY.plusDays(80));
		milestoneRepository.save(Milestone.builder().tenantId(acme.getId()).projectId(apollo.getId()).name("Launch")
				.targetDate(TODAY.plusDays(4)).build());
		String range = "?from=" + TODAY + "&to=" + TODAY.plusDays(30);
		getAs(avaToken, base(acme) + "/calendar" + range + "&scope=mine")
				.andExpect(jsonPath("$.tasks[*].title", hasItem("Ava due")))
				.andExpect(jsonPath("$.tasks[*].title", not(hasItem("Max due"))))
				.andExpect(jsonPath("$.milestones[0].name").value("Launch"));
		getAs(avaToken, base(acme) + "/calendar" + range + "&scope=workspace")
				.andExpect(jsonPath("$.tasks", hasSize(2)));
		getAs(avaToken, base(acme) + "/calendar?from=" + TODAY + "&to=" + TODAY.plusDays(200)).andExpect(status().isBadRequest());
		getAs(ozToken, base(acme) + "/calendar" + range).andExpect(status().isForbidden());
		getAs(ozToken, base(other) + "/calendar" + range + "&projectId=" + apollo.getId()).andExpect(status().isNotFound());
	}

	@Test
	void portfolioExplainsTargetDateBlockedAndMilestoneSignals() throws Exception {
		apollo.setStartDate(TODAY.minusDays(30));
		apollo.setTargetDate(TODAY.minusDays(1));
		projectRepository.save(apollo);
		Task a = task(apollo, "A", TaskStatus.TODO, ava, null);
		Task b = task(apollo, "B", TaskStatus.TODO, ava, null);
		dependencyRepository.save(TaskDependency.builder().tenantId(acme.getId()).blockingTaskId(a.getId()).blockedTaskId(b.getId()).build());
		milestoneRepository.save(Milestone.builder().tenantId(acme.getId()).projectId(apollo.getId()).name("Late")
				.targetDate(TODAY.minusDays(2)).build());

		getAs(maxToken, base(acme) + "/portfolio")
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].name").value("Apollo"))
				.andExpect(jsonPath("$[0].blockedTasks").value(1))
				.andExpect(jsonPath("$[0].milestonesOpen").value(1))
				.andExpect(jsonPath("$[0].signals[*].code", hasItem("TARGET_PASSED")))
				.andExpect(jsonPath("$[0].signals[*].code", hasItem("BLOCKED")))
				.andExpect(jsonPath("$[0].signals[*].code", hasItem("MILESTONE_OVERDUE")));
		getAs(ozToken, base(other) + "/portfolio").andExpect(jsonPath("$[*].name", not(hasItem("Apollo"))));
	}

	@Test
	void workloadUsesExplicitTaskCapacityAndOnlyAdminsSetOthersCapacity() throws Exception {
		task(apollo, "Late 1", TaskStatus.TODO, ava, TODAY.minusDays(2));
		task(apollo, "Late 2", TaskStatus.IN_PROGRESS, ava, TODAY.minusDays(1));
		task(apollo, "Nobody", TaskStatus.TODO, null, null);

		putAs(maxToken, base(acme) + "/members/" + ava.getId() + "/capacity", "{\"weeklyCapacity\":1}").andExpect(status().isForbidden());
		putAs(avaToken, base(acme) + "/members/" + ava.getId() + "/capacity", "{\"weeklyCapacity\":1}").andExpect(status().isOk());
		putAs(adaToken, base(acme) + "/members/" + ava.getId() + "/capacity", "{\"weeklyCapacity\":500}").andExpect(status().isBadRequest());
		putAs(ozToken, base(other) + "/members/" + ava.getId() + "/capacity", "{\"weeklyCapacity\":1}").andExpect(status().isNotFound());

		getAs(maxToken, base(acme) + "/workload")
				.andExpect(jsonPath("$.members[0].name").value("Ava"))
				.andExpect(jsonPath("$.members[0].open").value(2))
				.andExpect(jsonPath("$.members[0].overdue").value(2))
				.andExpect(jsonPath("$.members[0].weeklyCapacity").value(1))
				.andExpect(jsonPath("$.members[0].warning", containsString("capacity of 1 per week")))
				.andExpect(jsonPath("$.unassigned.open").value(1));
		getAs(maxToken, base(acme) + "/members/" + ava.getId() + "/overview")
				.andExpect(jsonPath("$.open", hasSize(2)))
				.andExpect(jsonPath("$.projects[0].projectName").value("Apollo"));
		getAs(ozToken, base(other) + "/members/" + ava.getId() + "/overview").andExpect(status().isNotFound());
	}
}
