package io.github.aryan52.tenantflow.activity.dto;

import io.github.aryan52.tenantflow.activity.ActivityEvent;
import java.time.Instant;
import java.util.UUID;

public record ActivityResponse(
		UUID id,
		String type,
		String summary,
		UUID actorId,
		String actorName,
		UUID projectId,
		UUID taskId,
		Instant createdAt
) {
	public static ActivityResponse from(ActivityEvent e, String actorName) {
		return new ActivityResponse(e.getId(), e.getType(), e.getSummary(), e.getActorId(), actorName,
				e.getProjectId(), e.getTaskId(), e.getCreatedAt());
	}
}
