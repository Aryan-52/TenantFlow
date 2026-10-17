package io.github.aryan52.tenantflow.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A task a user put on their "Today" list for a given day (V16). Personal: never shared. */
@Entity
@Table(name = "task_focus")
@IdClass(TaskFocusEntry.Key.class)
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TaskFocusEntry {

	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	public static class Key implements Serializable {
		private UUID userId;
		private UUID taskId;
	}

	@Id
	@Column(name = "user_id", nullable = false, columnDefinition = "uuid")
	private UUID userId;

	@Id
	@Column(name = "task_id", nullable = false, columnDefinition = "uuid")
	private UUID taskId;

	@Column(name = "focus_date", nullable = false)
	private LocalDate focusDate;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}
}
