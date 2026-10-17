package io.github.aryan52.tenantflow.task.dto;

import io.github.aryan52.tenantflow.entity.TaskPriority;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** {@code planning} is optional (start date, milestone, recurrence). */
public record CreateTaskRequest(
		@NotBlank @Size(max = 255) String title,
		@Size(max = 2000) String description,
		@NotNull TaskStatus status,
		@NotNull TaskPriority priority,
		UUID assigneeId,
		LocalDate dueDate,
		@Valid TaskPlanning planning
) {
	public CreateTaskRequest(String title, String description, TaskStatus status, TaskPriority priority,
			UUID assigneeId, LocalDate dueDate) {
		this(title, description, status, priority, assigneeId, dueDate, null);
	}
}
