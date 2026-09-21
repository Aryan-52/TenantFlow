package io.github.aryan52.tenantflow.project.dto;

import io.github.aryan52.tenantflow.entity.Project;
import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
		UUID id,
		UUID tenantId,
		String name,
		String description,
		Instant createdAt,
		Instant updatedAt
) {
	public static ProjectResponse from(Project project) {
		return new ProjectResponse(
				project.getId(),
				project.getTenant().getId(),
				project.getName(),
				project.getDescription(),
				project.getCreatedAt(),
				project.getUpdatedAt()
		);
	}
}
