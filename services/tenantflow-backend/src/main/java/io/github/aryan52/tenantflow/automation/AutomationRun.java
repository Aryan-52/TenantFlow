package io.github.aryan52.tenantflow.automation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** Execution history: one row per trigger handled; dedupe_key makes each trigger run once. */
@Entity
@Table(name = "automation_runs")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AutomationRun {

	public enum Outcome { SUCCESS, SKIPPED, FAILED }

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID id;

	@Column(name = "tenant_id", nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID tenantId;

	@Enumerated(EnumType.STRING)
	@Column(name = "rule_type", nullable = false, length = 64)
	private AutomationRuleType ruleType;

	@Column(name = "task_id", columnDefinition = "uuid")
	private UUID taskId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private Outcome outcome;

	@Column(nullable = false, length = 500)
	private String message;

	@Column(name = "dedupe_key", nullable = false, length = 200)
	private String dedupeKey;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		createdAt = io.github.aryan52.tenantflow.common.Timestamps.now();
	}
}
