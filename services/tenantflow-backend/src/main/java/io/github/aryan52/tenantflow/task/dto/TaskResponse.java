package io.github.aryan52.tenantflow.task.dto;

import io.github.aryan52.tenantflow.entity.Task;
import io.github.aryan52.tenantflow.entity.TaskPriority;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import io.github.aryan52.tenantflow.entity.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TaskResponse(
		UUID id,
		UUID projectId,
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
		Instant updatedAt
) {
	public static TaskResponse from(Task task) {
		User assignee = task.getAssignee();
		User createdBy = task.getCreatedBy();
		return new TaskResponse(
				task.getId(),
				task.getProject().getId(),
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
				task.getUpdatedAt()
		);
	}
}
