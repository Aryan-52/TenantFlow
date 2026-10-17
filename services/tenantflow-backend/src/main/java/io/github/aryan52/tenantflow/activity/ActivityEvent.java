package io.github.aryan52.tenantflow.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One entry of a workspace's activity history. Related ids are plain columns (not JPA
 * relations) so deleting a project or task never cascades into, or is blocked by, history.
 */
@Entity
@Table(name = "activity_events")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ActivityEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID id;

	@Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
	private UUID tenantId;

	@Column(name = "project_id", columnDefinition = "uuid")
	private UUID projectId;

	@Column(name = "task_id", columnDefinition = "uuid")
	private UUID taskId;

	@Column(name = "actor_id", columnDefinition = "uuid")
	private UUID actorId;

	@Column(nullable = false, length = 64)
	private String type;

	@Column(nullable = false, length = 500)
	private String summary;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = io.github.aryan52.tenantflow.common.Timestamps.now();
		}
	}
}
