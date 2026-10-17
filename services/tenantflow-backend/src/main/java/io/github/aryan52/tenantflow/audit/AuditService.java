package io.github.aryan52.tenantflow.audit;

import io.github.aryan52.tenantflow.audit.AuditEvent.Outcome;
import io.github.aryan52.tenantflow.audit.dto.AuditEventResponse;
import io.github.aryan52.tenantflow.common.PageResponse;
import io.github.aryan52.tenantflow.common.Pagination;
import io.github.aryan52.tenantflow.security.AuthenticatedUser;
import io.github.aryan52.tenantflow.security.CurrentUser;
import io.github.aryan52.tenantflow.web.RequestCorrelationFilter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes audit events and mirrors each one to the "AUDIT" log category (never with secrets).
 *
 * <p>{@link #record} joins the caller's transaction, so a success is only recorded when the
 * change itself commits. {@link #recordIndependently} commits on its own - for failures and
 * denials whose surrounding transaction is about to roll back.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

	private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AUDIT");
	private static final int MAX_DETAILS = 1000;

	private final AuditEventRepository repository;

	public record Entry(AuditAction action, Outcome outcome, UUID tenantId, String targetType, UUID targetId, String details) {
		public static Entry success(AuditAction action, UUID tenantId, String targetType, UUID targetId, String details) {
			return new Entry(action, Outcome.SUCCESS, tenantId, targetType, targetId, details);
		}

		public static Entry of(AuditAction action, Outcome outcome, UUID tenantId, String targetType, UUID targetId, String details) {
			return new Entry(action, outcome, tenantId, targetType, targetId, details);
		}
	}

	/** Records in the caller's transaction; the actor defaults to the authenticated user. */
	@Transactional(propagation = Propagation.REQUIRED)
	public void record(Entry entry) {
		save(entry, null, null);
	}

	/** Records in the caller's transaction for an explicit actor (e.g. during login). */
	@Transactional(propagation = Propagation.REQUIRED)
	public void record(Entry entry, UUID actorId, String actorEmail) {
		save(entry, actorId, actorEmail);
	}

	/** Commits independently of the caller - use for failures and denials. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordIndependently(Entry entry, UUID actorId, String actorEmail) {
		save(entry, actorId, actorEmail);
	}

	@Transactional(readOnly = true)
	public PageResponse<AuditEventResponse> listForTenant(UUID tenantId, Integer page, Integer size) {
		return PageResponse.of(
				repository.findByTenantId(tenantId, Pagination.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"))),
				AuditEventResponse::from
		);
	}

	private void save(Entry entry, UUID actorId, String actorEmail) {
		AuthenticatedUser current = CurrentUser.get().orElse(null);
		UUID actor = actorId != null ? actorId : current != null ? current.getId() : null;
		String email = actorEmail != null ? actorEmail : current != null ? current.getEmail() : null;
		String details = entry.details();
		if (details != null && details.length() > MAX_DETAILS) {
			details = details.substring(0, MAX_DETAILS);
		}

		AuditEvent event = repository.save(AuditEvent.builder()
				.tenantId(entry.tenantId())
				.actorId(actor)
				.actorEmail(email)
				.action(entry.action())
				.outcome(entry.outcome())
				.targetType(entry.targetType())
				.targetId(entry.targetId())
				.details(details)
				.ipAddress(clientIp())
				.requestId(MDC.get(RequestCorrelationFilter.MDC_KEY))
				.build());

		AUDIT_LOG.info("action={} outcome={} tenant={} actor={} target={}:{} event={}",
				event.getAction(), event.getOutcome(), event.getTenantId(), event.getActorId(),
				event.getTargetType(), event.getTargetId(), event.getId());
	}

	private static String clientIp() {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
			// getRemoteAddr() already honours X-Forwarded-For from the trusted reverse proxy
			// (server.forward-headers-strategy=native); the raw header is never trusted here.
			return attributes.getRequest().getRemoteAddr();
		}
		return null;
	}
}
