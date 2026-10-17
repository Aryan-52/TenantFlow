package io.github.aryan52.tenantflow.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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

/** A message in a task's discussion (see V14__create_task_comments.sql). */
@Entity
@Table(name = "task_comments")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TaskComment {

	public static final int BODY_MAX = 4000;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID id;

	@Column(name = "tenant_id", nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID tenantId;

	@Column(name = "task_id", nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID taskId;

	/** Null once the author's account is deleted. */
	@Column(name = "author_id", updatable = false, columnDefinition = "uuid")
	private UUID authorId;

	@Column(nullable = false, length = BODY_MAX)
	private String body;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
	private Instant updatedAt;

	@PrePersist
	void onCreate() {
		Instant now = io.github.aryan52.tenantflow.common.Timestamps.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = io.github.aryan52.tenantflow.common.Timestamps.now();
	}
}
