package io.github.aryan52.tenantflow.notification;

import io.github.aryan52.tenantflow.common.PageResponse;
import io.github.aryan52.tenantflow.notification.dto.NotificationResponse;
import io.github.aryan52.tenantflow.security.AuthenticatedUser;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Notifications are per user (across workspaces), so these routes are not tenant-prefixed. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;

	@GetMapping
	public PageResponse<NotificationResponse> list(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(defaultValue = "false") boolean unreadOnly,
			@RequestParam(required = false) String category,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size
	) {
		return notificationService.list(user.getId(), unreadOnly, category, page, size);
	}

	@GetMapping("/unread-count")
	public Map<String, Long> unreadCount(@AuthenticationPrincipal AuthenticatedUser user) {
		return Map.of("count", notificationService.unreadCount(user.getId()));
	}

	@PostMapping("/{notificationId}/read")
	public NotificationResponse markRead(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID notificationId) {
		return notificationService.markRead(user.getId(), notificationId);
	}

	@PostMapping("/read-all")
	public Map<String, Integer> markAllRead(@AuthenticationPrincipal AuthenticatedUser user) {
		return Map.of("updated", notificationService.markAllRead(user.getId()));
	}
}
