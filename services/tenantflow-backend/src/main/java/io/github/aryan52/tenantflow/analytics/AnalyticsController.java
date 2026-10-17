package io.github.aryan52.tenantflow.analytics;

import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Workspace analytics, optionally for one project. Any workspace member may view them. */
@RestController
@RequestMapping("/api/tenants/{tenantId}/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

	private final AnalyticsService analyticsService;

	@GetMapping
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public AnalyticsResponse analytics(
			@PathVariable UUID tenantId,
			@RequestParam(required = false) UUID projectId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
	) {
		return analyticsService.analytics(tenantId, projectId, from, to);
	}
}
