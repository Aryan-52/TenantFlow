package io.github.aryan52.tenantflow.activity;

import io.github.aryan52.tenantflow.activity.dto.ActivityResponse;
import io.github.aryan52.tenantflow.common.PageResponse;
import io.github.aryan52.tenantflow.common.Pagination;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.notification.Notification;
import io.github.aryan52.tenantflow.notification.NotificationRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.security.CurrentUser;
import io.github.aryan52.tenantflow.security.AuthenticatedUser;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records activity and the notifications it causes, in the caller's transaction: if the
 * change rolls back, so do its history and notifications (no outbox needed for in-app
 * delivery). Notifications are deduplicated per recipient by a key that callers derive
 * from the entity's new optimistic-lock version, so one state change notifies once.
 */
@Service
@RequiredArgsConstructor
public class ActivityService {

	/** A notification to send as part of an activity. {@code workspaceScoped=false} keeps it
	 * visible after the recipient leaves the workspace (used for "you were removed"). */
	public record Notify(UUID recipientId, String type, String title, String body, String link, String dedupeKey,
			boolean workspaceScoped) {
		public static Notify of(UUID recipientId, String type, String title, String body, String link, String dedupeKey) {
			return new Notify(recipientId, type, title, body, link, dedupeKey, true);
		}
	}

	private final ActivityEventRepository activityRepository;
	private final NotificationRepository notificationRepository;
	private final UserRepository userRepository;

	@Transactional(propagation = Propagation.REQUIRED)
	public ActivityEvent record(UUID tenantId, UUID projectId, UUID taskId, String type, String summary, List<Notify> notify) {
		UUID actorId = CurrentUser.get().map(AuthenticatedUser::getId).orElse(null);
		ActivityEvent event = activityRepository.save(ActivityEvent.builder()
				.tenantId(tenantId)
				.projectId(projectId)
				.taskId(taskId)
				.actorId(actorId)
				.type(type)
				.summary(truncate(summary, 500))
				.build());

		for (Notify n : notify) {
			// Never notify people about their own actions, and never twice for one change.
			if (n.recipientId() == null || Objects.equals(n.recipientId(), actorId)
					|| notificationRepository.existsByRecipientIdAndDedupeKey(n.recipientId(), n.dedupeKey())) {
				continue;
			}
			notificationRepository.save(Notification.builder()
					.recipientId(n.recipientId())
					.tenantId(n.workspaceScoped() ? tenantId : null)
					.activityId(n.workspaceScoped() ? event.getId() : null)
					.type(n.type())
					.title(truncate(n.title(), 200))
					.body(truncate(n.body(), 500))
					.link(n.link())
					.dedupeKey(truncate(n.dedupeKey(), 200))
					.build());
		}
		return event;
	}

	@Transactional(readOnly = true)
	public PageResponse<ActivityResponse> list(UUID tenantId, UUID projectId, UUID taskId, Integer page, Integer size) {
		var pageable = Pagination.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
		// Always tenant-scoped: an id from another workspace simply matches nothing.
		Page<ActivityEvent> events = taskId != null
				? activityRepository.findByTenantIdAndTaskId(tenantId, taskId, pageable)
				: projectId != null
						? activityRepository.findByTenantIdAndProjectId(tenantId, projectId, pageable)
						: activityRepository.findByTenantId(tenantId, pageable);

		// One query for all actor names on the page (no N+1).
		var actorIds = events.getContent().stream().map(ActivityEvent::getActorId).filter(Objects::nonNull).collect(Collectors.toSet());
		Map<UUID, String> names = userRepository.findAllById(actorIds).stream()
				.collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));
		Function<ActivityEvent, ActivityResponse> mapper = e -> ActivityResponse.from(e, names.get(e.getActorId()));
		return PageResponse.of(events, mapper);
	}

	private static String truncate(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
	}
}
