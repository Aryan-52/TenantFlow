package io.github.aryan52.tenantflow.task.dto;

import io.github.aryan52.tenantflow.entity.TaskPriority;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Full replacement of a task's editable fields: a null {@code assigneeId} or {@code dueDate}
 * clears it. {@code projectId} is optional; when set to another project in the same tenant
 * the task is moved there. {@code version} is optional: when sent, the update is rejected
 * with 409 if the task has changed since that version was read (no lost updates).
 * {@code planning} is optional: omitted = keep the task's planning fields (see {@link TaskPlanning}).
 */
public record UpdateTaskRequest(
		@NotBlank @Size(max = 255) String title,
		@Size(max = 2000) String description,
		@NotNull TaskStatus status,
		@NotNull TaskPriority priority,
		UUID assigneeId,
		LocalDate dueDate,
		UUID projectId,
		Long version,
		@Valid TaskPlanning planning
) {
	public UpdateTaskRequest(String title, String description, TaskStatus status, TaskPriority priority,
			UUID assigneeId, LocalDate dueDate, UUID projectId, Long version) {
		this(title, description, status, priority, assigneeId, dueDate, projectId, version, null);
	}

	/** Without a version check (last write wins). */
	public UpdateTaskRequest(String title, String description, TaskStatus status, TaskPriority priority,
			UUID assigneeId, LocalDate dueDate, UUID projectId) {
		this(title, description, status, priority, assigneeId, dueDate, projectId, null, null);
	}
}
