package io.github.aryan52.tenantflow.project.dto;

import io.github.aryan52.tenantflow.entity.Project;
import io.github.aryan52.tenantflow.entity.ProjectStatus;
import io.github.aryan52.tenantflow.entity.User;
import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
		UUID id,
		UUID tenantId,
		String name,
		String description,
		ProjectStatus status,
		UUID createdById,
		String createdByName,
		UUID ownerId,
		String ownerName,
		long taskCount,
		long completedTaskCount,
		Instant createdAt,
		Instant updatedAt,
		long version,
		java.time.LocalDate startDate,
		java.time.LocalDate targetDate
) {
	public static ProjectResponse from(Project project, long taskCount, long completedTaskCount) {
		User createdBy = project.getCreatedBy();
		User owner = project.getOwner();
		return new ProjectResponse(
				project.getId(),
				project.getTenant().getId(),
				project.getName(),
				project.getDescription(),
				project.getStatus(),
				createdBy != null ? createdBy.getId() : null,
				createdBy != null ? createdBy.getName() : null,
				owner != null ? owner.getId() : null,
				owner != null ? owner.getName() : null,
				taskCount,
				completedTaskCount,
				project.getCreatedAt(),
				project.getUpdatedAt(),
				project.getVersion(),
				project.getStartDate(),
				project.getTargetDate()
		);
	}
}
