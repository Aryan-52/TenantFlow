package io.github.aryan52.tenantflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Task {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
			name = "project_id",
			nullable = false,
			foreignKey = @ForeignKey(name = "fk_tasks_project")
	)
	private Project project;

	@NotBlank
	@Size(max = 255)
	@Column(nullable = false, length = 255)
	private String title;

	@Size(max = 2000)
	@Column(length = 2000)
	private String description;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private TaskStatus status;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private TaskPriority priority;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(
			name = "assignee_id",
			foreignKey = @ForeignKey(name = "fk_tasks_assignee")
	)
	private User assignee;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(
			name = "created_by",
			foreignKey = @ForeignKey(name = "fk_tasks_created_by")
	)
	private User createdBy;

	@Column(name = "due_date")
	private LocalDate dueDate;

	/** When work is planned to start (V16). Never inferred: null until someone sets it. */
	@Column(name = "start_date")
	private LocalDate startDate;

	/** Optional milestone of the same project (V16). A plain id so deleting a milestone only clears it. */
	@Column(name = "milestone_id", columnDefinition = "uuid")
	private UUID milestoneId;

	/** Recurrence rule (V16), copied to each occurrence; null = not recurring. */
	@Enumerated(EnumType.STRING)
	@Column(name = "recurrence_frequency", length = 16)
	private RecurrenceFrequency recurrenceFrequency;

	@Column(name = "recurrence_interval")
	private Integer recurrenceInterval;

	/** WEEKLY only: bit 0 = Monday ... bit 6 = Sunday; null/0 = the due date's weekday. */
	@Column(name = "recurrence_weekdays")
	private Short recurrenceWeekdays;

	@Column(name = "recurrence_until")
	private LocalDate recurrenceUntil;

	/** Shared by every occurrence of one recurring task. */
	@Column(name = "series_id", columnDefinition = "uuid")
	private UUID seriesId;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
	private Instant updatedAt;

	/**
	 * When the task was moved to Done (V15); null while open, and null for tasks completed
	 * before completion times were recorded. Maintained by the lifecycle hooks below so every
	 * code path that changes the status keeps it consistent.
	 */
	@Column(name = "completed_at", columnDefinition = "timestamp with time zone")
	private Instant completedAt;

	/** Optimistic-locking version (V9). Incremented by Hibernate on every update. */
	@Version
	@Column(nullable = false)
	private long version;

	@PrePersist
	void onCreate() {
		Instant now = io.github.aryan52.tenantflow.common.Timestamps.now();
		createdAt = now;
		updatedAt = now;
		syncCompletedAt(now);
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = io.github.aryan52.tenantflow.common.Timestamps.now();
		syncCompletedAt(updatedAt);
	}

	private void syncCompletedAt(Instant now) {
		if (status == TaskStatus.DONE) {
			if (completedAt == null) {
				completedAt = now;
			}
		} else {
			completedAt = null;
		}
	}
}
