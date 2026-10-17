package io.github.aryan52.tenantflow.notification;

import io.github.aryan52.tenantflow.common.PageResponse;
import io.github.aryan52.tenantflow.common.Pagination;
import io.github.aryan52.tenantflow.error.ResourceNotFoundException;
import io.github.aryan52.tenantflow.notification.dto.NotificationResponse;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The current user's notifications only; see {@link NotificationRepository} for visibility. */
@Service
@RequiredArgsConstructor
public class NotificationService {

	private final NotificationRepository repository;

	@Transactional(readOnly = true)
	public PageResponse<NotificationResponse> list(UUID userId, boolean unreadOnly, Integer page, Integer size) {
		return PageResponse.of(
				repository.findVisible(userId, unreadOnly, Pagination.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))),
				NotificationResponse::from);
	}

	/** Inbox categories: groups of notification types. */
	public static final java.util.Map<String, java.util.List<String>> CATEGORIES = java.util.Map.of(
			"assigned", java.util.List.of("TASK_ASSIGNED", "TASK_UNASSIGNED", "PROJECT_OWNER_ASSIGNED"),
			"mentions", java.util.List.of("MENTION", "TASK_COMMENTED"),
			"reminders", java.util.List.of("REMINDER_DUE_SOON", "REMINDER_OVERDUE"),
			"updates", java.util.List.of("TASK_STATUS_CHANGED", "TASK_DUE_DATE_CHANGED", "TASK_COMPLETED", "TASK_DELETED"),
			"automations", java.util.List.of("AUTOMATION_URGENT", "AUTOMATION_OVERDUE", "AUTOMATION_UNBLOCKED", "AUTOMATION_MILESTONE"));

	@Transactional(readOnly = true)
	public PageResponse<NotificationResponse> list(UUID userId, boolean unreadOnly, String category, Integer page, Integer size) {
		if (category == null || category.isBlank() || "all".equals(category)) {
			return list(userId, unreadOnly, page, size);
		}
		java.util.List<String> types = CATEGORIES.get(category);
		if (types == null) {
			throw new IllegalArgumentException("category must be one of: all, " + String.join(", ", new java.util.TreeSet<>(CATEGORIES.keySet())));
		}
		return PageResponse.of(
				repository.findVisibleOfTypes(userId, unreadOnly, types, Pagination.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))),
				NotificationResponse::from);
	}

	@Transactional(readOnly = true)
	public long unreadCount(UUID userId) {
		return repository.countUnread(userId);
	}

	/** Idempotent: marking an already-read notification read again is a no-op. */
	@Transactional
	public NotificationResponse markRead(UUID userId, UUID notificationId) {
		Notification n = repository.findVisibleById(notificationId, userId)
				.orElseThrow(() -> new ResourceNotFoundException("Notification"));
		if (n.getReadAt() == null) {
			n.setReadAt(Instant.now());
		}
		return NotificationResponse.from(n);
	}

	@Transactional
	public int markAllRead(UUID userId) {
		return repository.markAllRead(userId, Instant.now());
	}
}
