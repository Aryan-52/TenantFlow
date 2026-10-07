package io.github.aryan52.tenantflow.task.dto;

import io.github.aryan52.tenantflow.entity.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTaskStatusRequest(@NotNull TaskStatus status) {}
