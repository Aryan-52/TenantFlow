package io.github.aryan52.tenantflow.task.dto;

import io.github.aryan52.tenantflow.entity.RecurrenceFrequency;
import io.github.aryan52.tenantflow.entity.Task;
import io.github.aryan52.tenantflow.entity.TaskPriority;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import io.github.aryan52.tenantflow.entity.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code openBlockers}: unfinished tasks this one waits on; null where it was not computed
 * (lightweight lists), so clients must not read null as "not blocked".
 */
public record TaskResponse(
		UUID id,
		UUID projectId,
		String projectName,
		String title,
		String description,
		TaskStatus status,
		TaskPriority priority,
		UUID assigneeId,
		String assigneeName,
		UUID createdById,
		String createdByName,
		LocalDate dueDate,
		Instant createdAt,
		Instant updatedAt,
		long version,
		LocalDate startDate,
		UUID milestoneId,
		Recurrence recurrence,
		Instant completedAt,
		Integer openBlockers
) {
	public record Recurrence(RecurrenceFrequency frequency, int interval, int weekdays, LocalDate until) {
	}

	public static TaskResponse from(Task task) {
		return from(task, null);
	}

	public static TaskResponse from(Task task, Integer openBlockers) {
		User assignee = task.getAssignee();
		User createdBy = task.getCreatedBy();
		Recurrence recurrence = task.getRecurrenceFrequency() == null ? null
				: new Recurrence(task.getRecurrenceFrequency(),
						task.getRecurrenceInterval() == null ? 1 : task.getRecurrenceInterval(),
						task.getRecurrenceWeekdays() == null ? 0 : task.getRecurrenceWeekdays(),
						task.getRecurrenceUntil());
		return new TaskResponse(
				task.getId(),
				task.getProject().getId(),
				task.getProject().getName(),
				task.getTitle(),
				task.getDescription(),
				task.getStatus(),
				task.getPriority(),
				assignee != null ? assignee.getId() : null,
				assignee != null ? assignee.getName() : null,
				createdBy != null ? createdBy.getId() : null,
				createdBy != null ? createdBy.getName() : null,
				task.getDueDate(),
				task.getCreatedAt(),
				task.getUpdatedAt(),
				task.getVersion(),
				task.getStartDate(),
				task.getMilestoneId(),
				recurrence,
				task.getCompletedAt(),
				openBlockers
		);
	}
}
