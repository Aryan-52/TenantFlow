package io.github.aryan52.tenantflow.task;

import io.github.aryan52.tenantflow.activity.ActivityService;
import io.github.aryan52.tenantflow.activity.ActivityService.Notify;
import io.github.aryan52.tenantflow.entity.Task;
import io.github.aryan52.tenantflow.entity.TaskPriority;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import io.github.aryan52.tenantflow.entity.User;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Turns task changes into activity entries and notifications. Only real changes produce
 * events (re-sending an unchanged task records nothing), and notification dedupe keys
 * include the task's new version, so each state transition notifies a person at most once.
 * Summaries omit the actor; the UI prefixes the actor's current name.
 */
@Component
@RequiredArgsConstructor
class TaskActivity {

	private final ActivityService activityService;

	/** Task state captured before a mutation. */
	record Snapshot(String title, String description, TaskStatus status, TaskPriority priority,
			UUID assigneeId, LocalDate dueDate, UUID projectId, String projectName) {
		static Snapshot of(Task t) {
			return new Snapshot(t.getTitle(), t.getDescription(), t.getStatus(), t.getPriority(),
					t.getAssignee() == null ? null : t.getAssignee().getId(), t.getDueDate(),
					t.getProject().getId(), t.getProject().getName());
		}
	}

	void created(UUID tenantId, Task task) {
		List<Notify> notify = new ArrayList<>();
		User assignee = task.getAssignee();
		if (assignee != null) {
			notify.add(assigned(tenantId, task, assignee));
		}
		activityService.record(tenantId, task.getProject().getId(), task.getId(), "TASK_CREATED",
				"created task " + quote(task) + " in " + task.getProject().getName(), notify);
	}

	void updated(UUID tenantId, Snapshot before, Task task) {
		UUID projectId = task.getProject().getId();
		User assignee = task.getAssignee();
		UUID assigneeId = assignee == null ? null : assignee.getId();

		if (!Objects.equals(before.projectId(), projectId)) {
			record(tenantId, task, "TASK_MOVED", "moved " + quote(task) + " from " + before.projectName()
					+ " to " + task.getProject().getName(), List.of());
		}
		if (!Objects.equals(before.assigneeId(), assigneeId)) {
			// The person taken off the task hears about it too (never when they did it themselves).
			List<Notify> notify = new ArrayList<>();
			if (before.assigneeId() != null) {
				notify.add(Notify.of(before.assigneeId(), "TASK_UNASSIGNED", "No longer assigned to you: " + task.getTitle(),
						"In " + task.getProject().getName(), link(tenantId, task),
						"task:" + task.getId() + ":v" + task.getVersion() + ":unassigned:" + before.assigneeId()));
			}
			if (assignee == null) {
				record(tenantId, task, "TASK_UNASSIGNED", "unassigned " + quote(task), notify);
			} else {
				notify.add(assigned(tenantId, task, assignee));
				record(tenantId, task, "TASK_ASSIGNED", "assigned " + quote(task) + " to " + assignee.getName(), notify);
			}
		}
		if (before.status() != task.getStatus()) {
			statusChanged(tenantId, task);
		}
		if (!Objects.equals(before.dueDate(), task.getDueDate())) {
			String what = task.getDueDate() == null ? "removed the due date of " + quote(task)
					: "set the due date of " + quote(task) + " to " + task.getDueDate();
			record(tenantId, task, "TASK_DUE_DATE_CHANGED", what, assigneeOnly(tenantId, task, "TASK_DUE_DATE_CHANGED",
					task.getDueDate() == null ? "Due date removed: " + task.getTitle() : "Due " + task.getDueDate() + ": " + task.getTitle(),
					"due:" + task.getDueDate()));
		}
		if (before.priority() != task.getPriority()) {
			record(tenantId, task, "TASK_PRIORITY_CHANGED", "changed the priority of " + quote(task) + " to "
					+ label(task.getPriority().name()), List.of());
		}
		if (!Objects.equals(before.title(), task.getTitle()) || !Objects.equals(before.description(), task.getDescription())) {
			record(tenantId, task, "TASK_EDITED", "edited " + quote(task), List.of());
		}
	}

	void statusChanged(UUID tenantId, Task task) {
		List<Notify> notify = new ArrayList<>(assigneeOnly(tenantId, task, "TASK_STATUS_CHANGED",
				task.getTitle() + " is now " + label(task.getStatus().name()), "status:" + task.getStatus()));
		// Whoever created the task learns when it is finished (if they are not the assignee).
		User creator = task.getCreatedBy();
		if (task.getStatus() == TaskStatus.DONE && creator != null
				&& (task.getAssignee() == null || !creator.getId().equals(task.getAssignee().getId()))) {
			notify.add(Notify.of(creator.getId(), "TASK_COMPLETED", "Completed: " + task.getTitle(),
					"In " + task.getProject().getName(), link(tenantId, task),
					"task:" + task.getId() + ":v" + task.getVersion() + ":done:" + creator.getId()));
		}
		record(tenantId, task, "TASK_STATUS_CHANGED", "moved " + quote(task) + " to " + label(task.getStatus().name()), notify);
	}

	void deleted(UUID tenantId, Task task) {
		User assignee = task.getAssignee();
		List<Notify> notify = assignee == null ? List.of() : List.of(Notify.of(assignee.getId(), "TASK_DELETED",
				"Task deleted: " + task.getTitle(), "In " + task.getProject().getName(),
				"/tenants/" + tenantId + "/projects/" + task.getProject().getId(),
				"task:" + task.getId() + ":deleted"));
		activityService.record(tenantId, task.getProject().getId(), task.getId(), "TASK_DELETED",
				"deleted task " + quote(task), notify);
	}

	private void record(UUID tenantId, Task task, String type, String summary, List<Notify> notify) {
		activityService.record(tenantId, task.getProject().getId(), task.getId(), type, summary, notify);
	}

	private Notify assigned(UUID tenantId, Task task, User assignee) {
		return Notify.of(assignee.getId(), "TASK_ASSIGNED", "You were assigned: " + task.getTitle(),
				"In " + task.getProject().getName(), link(tenantId, task),
				"task:" + task.getId() + ":v" + task.getVersion() + ":assigned:" + assignee.getId());
	}

	/** Notifies the current assignee about a change (never the actor; ActivityService skips that). */
	private List<Notify> assigneeOnly(UUID tenantId, Task task, String type, String title, String change) {
		User assignee = task.getAssignee();
		if (assignee == null) {
			return List.of();
		}
		return List.of(Notify.of(assignee.getId(), type, title, "In " + task.getProject().getName(), link(tenantId, task),
				"task:" + task.getId() + ":v" + task.getVersion() + ":" + change));
	}

	static String link(UUID tenantId, Task task) {
		return "/tenants/" + tenantId + "/projects/" + task.getProject().getId() + "/tasks?task=" + task.getId();
	}

	private static String quote(Task task) {
		return "\"" + task.getTitle() + "\"";
	}

	private static String label(String enumName) {
		String s = enumName.replace('_', ' ').toLowerCase();
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
