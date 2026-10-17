package io.github.aryan52.tenantflow.tenant.dto;

import io.github.aryan52.tenantflow.entity.ProjectStatus;
import java.time.Instant;
import java.util.UUID;

public record ProjectProgress(
		UUID id,
		String name,
		ProjectStatus status,
		/** Null when the project has no owner. */
		String ownerName,
		long totalTasks,
		long completedTasks,
		Instant updatedAt
) {}
