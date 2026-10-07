package io.github.aryan52.tenantflow.task.dto;

import io.github.aryan52.tenantflow.entity.TaskPriority;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Full replacement of a task's editable fields: a null {@code assigneeId} or {@code dueDate}
 * clears it. {@code projectId} is optional; when set to another project in the same tenant
 * the task is moved there.
 */
public record UpdateTaskRequest(
		@NotBlank @Size(max = 255) String title,
		@Size(max = 2000) String description,
		@NotNull TaskStatus status,
		@NotNull TaskPriority priority,
		UUID assigneeId,
		LocalDate dueDate,
		UUID projectId
) {}
