package io.github.aryan52.tenantflow.preferences;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A user's reminder and notification settings (V16). No row = the defaults below. */
@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class NotificationPreference {

	@Id
	@Column(name = "user_id", nullable = false, columnDefinition = "uuid")
	private UUID userId;

	@Builder.Default
	@Column(name = "due_soon_enabled", nullable = false)
	private boolean dueSoonEnabled = true;

	/** Remind this many days before the due date (0 = on the day). */
	@Builder.Default
	@Column(name = "due_soon_days", nullable = false)
	private int dueSoonDays = 1;

	@Builder.Default
	@Column(name = "overdue_enabled", nullable = false)
	private boolean overdueEnabled = true;

	@Builder.Default
	@Column(name = "mentions_enabled", nullable = false)
	private boolean mentionsEnabled = true;

	/** IANA zone id used to decide what "today" is for this user; null = server zone. */
	@Column(name = "time_zone", length = 64)
	private String timeZone;

	@Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
	private Instant updatedAt;

	@PrePersist
	@PreUpdate
	void touch() {
		updatedAt = Instant.now();
	}

	public static NotificationPreference defaults(UUID userId) {
		return NotificationPreference.builder().userId(userId).build();
	}
}
