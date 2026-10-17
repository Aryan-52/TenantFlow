package io.github.aryan52.tenantflow.audit;

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

@Entity
@Table(name = "audit_events")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AuditEvent {

	public enum Outcome { SUCCESS, FAILURE, DENIED }

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID id;

	@Column(name = "occurred_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
	private Instant occurredAt;

	@Column(name = "tenant_id", columnDefinition = "uuid")
	private UUID tenantId;

	@Column(name = "actor_id", columnDefinition = "uuid")
	private UUID actorId;

	@Column(name = "actor_email", length = 320)
	private String actorEmail;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 64)
	private AuditAction action;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private Outcome outcome;

	@Column(name = "target_type", length = 32)
	private String targetType;

	@Column(name = "target_id", columnDefinition = "uuid")
	private UUID targetId;

	@Column(length = 1000)
	private String details;

	@Column(name = "ip_address", length = 64)
	private String ipAddress;

	@Column(name = "request_id", length = 64)
	private String requestId;

	@PrePersist
	void onCreate() {
		if (occurredAt == null) {
			occurredAt = io.github.aryan52.tenantflow.common.Timestamps.now();
		}
	}
}
