package io.github.aryan52.tenantflow.audit.dto;

import io.github.aryan52.tenantflow.audit.AuditEvent;
import java.time.Instant;
import java.util.UUID;

/** IP addresses are recorded for investigations but intentionally not exposed through the API. */
public record AuditEventResponse(
		UUID id,
		Instant occurredAt,
		String action,
		String outcome,
		UUID actorId,
		String actorEmail,
		String targetType,
		UUID targetId,
		String details,
		String requestId
) {
	public static AuditEventResponse from(AuditEvent e) {
		return new AuditEventResponse(e.getId(), e.getOccurredAt(), e.getAction().name(), e.getOutcome().name(),
				e.getActorId(), e.getActorEmail(), e.getTargetType(), e.getTargetId(), e.getDetails(), e.getRequestId());
	}
}
