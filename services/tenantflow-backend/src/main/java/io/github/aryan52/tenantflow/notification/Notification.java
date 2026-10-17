package io.github.aryan52.tenantflow.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notifications", uniqueConstraints = @UniqueConstraint(
		name = "uk_notifications_recipient_dedupe", columnNames = {"recipient_id", "dedupe_key"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID id;

	@Column(name = "recipient_id", nullable = false, columnDefinition = "uuid")
	private UUID recipientId;

	/** Null for notices that stay visible after the recipient left the workspace. */
	@Column(name = "tenant_id", columnDefinition = "uuid")
	private UUID tenantId;

	@Column(name = "activity_id", columnDefinition = "uuid")
	private UUID activityId;

	@Column(nullable = false, length = 64)
	private String type;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(length = 500)
	private String body;

	/** In-app route, e.g. /tenants/{id}/projects/{id}/tasks?task={id}. Never an external URL. */
	@Column(length = 500)
	private String link;

	@Column(name = "dedupe_key", nullable = false, length = 200)
	private String dedupeKey;

	@Setter
	@Column(name = "read_at", columnDefinition = "timestamp with time zone")
	private Instant readAt;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = io.github.aryan52.tenantflow.common.Timestamps.now();
		}
	}
}
