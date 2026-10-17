package io.github.aryan52.tenantflow.notification.dto;

import io.github.aryan52.tenantflow.notification.Notification;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
		UUID id,
		String type,
		String title,
		String body,
		String link,
		UUID tenantId,
		boolean read,
		Instant createdAt
) {
	public static NotificationResponse from(Notification n) {
		return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(),
				n.getTenantId(), n.getReadAt() != null, n.getCreatedAt());
	}
}
