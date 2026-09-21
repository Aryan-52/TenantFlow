package io.github.aryan52.tenantflow.task.dto;

import io.github.aryan52.tenantflow.entity.Task;
import io.github.aryan52.tenantflow.entity.TaskPriority;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import java.time.Instant;
import java.util.UUID;

public record TaskResponse(
		UUID id,
		UUID projectId,
		String title,
		String description,
		TaskStatus status,
		TaskPriority priority,
		UUID assigneeId,
		Instant createdAt,
		Instant updatedAt
) {
	public static TaskResponse from(Task task) {
		return new TaskResponse(
				task.getId(),
				task.getProject().getId(),
				task.getTitle(),
				task.getDescription(),
				task.getStatus(),
				task.getPriority(),
				task.getAssignee() != null ? task.getAssignee().getId() : null,
				task.getCreatedAt(),
				task.getUpdatedAt()
		);
	}
}
