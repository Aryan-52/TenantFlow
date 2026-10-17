package io.github.aryan52.tenantflow.planning;

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

/** "blockingTask blocks blockedTask": the blocked task cannot be finished first (V16). */
@Entity
@Table(name = "task_dependencies")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TaskDependency {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID id;

	@Column(name = "tenant_id", nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID tenantId;

	@Column(name = "blocking_task_id", nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID blockingTaskId;

	@Column(name = "blocked_task_id", nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID blockedTaskId;

	@Column(name = "created_by", columnDefinition = "uuid")
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}
}
