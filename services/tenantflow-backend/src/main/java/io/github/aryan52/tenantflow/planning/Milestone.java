package io.github.aryan52.tenantflow.planning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A dated checkpoint of a project (V16). Tasks may point at it through tasks.milestone_id. */
@Entity
@Table(name = "project_milestones")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Milestone {

	public enum Status { OPEN, DONE }

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID id;

	@Column(name = "tenant_id", nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID tenantId;

	@Column(name = "project_id", nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID projectId;

	@Column(nullable = false, length = 160)
	private String name;

	@Column(length = 2000)
	private String description;

	@Column(name = "target_date", nullable = false)
	private LocalDate targetDate;

	@Builder.Default
	@jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
	@Column(nullable = false, length = 16)
	private Status status = Status.OPEN;

	@Column(name = "completed_at", columnDefinition = "timestamp with time zone")
	private Instant completedAt;

	@Column(name = "created_by", columnDefinition = "uuid")
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	@PrePersist
	void onCreate() {
		createdAt = io.github.aryan52.tenantflow.common.Timestamps.now();
		updatedAt = createdAt;
		syncCompletedAt();
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = io.github.aryan52.tenantflow.common.Timestamps.now();
		syncCompletedAt();
	}

	private void syncCompletedAt() {
		if (status == Status.DONE) {
			if (completedAt == null) {
				completedAt = updatedAt;
			}
		} else {
			completedAt = null;
		}
	}
}
