package io.github.aryan52.tenantflow.audit;

import io.github.aryan52.tenantflow.audit.dto.AuditEventResponse;
import io.github.aryan52.tenantflow.common.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/{tenantId}/audit-events")
@RequiredArgsConstructor
public class AuditController {

	private final AuditService auditService;

	@GetMapping
	@PreAuthorize("@tenantSecurity.hasRole(#tenantId, 'ADMIN', 'OWNER')")
	public PageResponse<AuditEventResponse> list(
			@PathVariable UUID tenantId,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size
	) {
		return auditService.listForTenant(tenantId, page, size);
	}
}
