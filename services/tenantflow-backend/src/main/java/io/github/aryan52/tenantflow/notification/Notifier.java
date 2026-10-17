package io.github.aryan52.tenantflow.notification;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Creates a notification that is not tied to a recorded activity (reminders, mentions,
 * automations). Deduplicated per recipient by {@code dedupeKey} (unique in the database too),
 * so retries, restarts and repeated scheduler runs never notify twice.
 */
@Component
@RequiredArgsConstructor
public class Notifier {

	private final NotificationRepository repository;

	/** @return true when a notification was created, false when it already existed. */
	public boolean notifyOnce(UUID recipientId, UUID tenantId, String type, String title, String body, String link,
			String dedupeKey) {
		if (recipientId == null || repository.existsByRecipientIdAndDedupeKey(recipientId, dedupeKey)) {
			return false;
		}
		repository.save(Notification.builder()
				.recipientId(recipientId)
				.tenantId(tenantId)
				.type(type)
				.title(truncate(title, 200))
				.body(truncate(body, 500))
				.link(link)
				.dedupeKey(truncate(dedupeKey, 200))
				.build());
		return true;
	}

	private static String truncate(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
	}
}
