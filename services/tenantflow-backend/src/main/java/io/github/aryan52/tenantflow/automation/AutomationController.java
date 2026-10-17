package io.github.aryan52.tenantflow.automation;

import io.github.aryan52.tenantflow.automation.AutomationService.RuleView;
import io.github.aryan52.tenantflow.automation.AutomationService.RunView;
import io.github.aryan52.tenantflow.common.PageResponse;
import io.github.aryan52.tenantflow.security.AuthenticatedUser;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Members can see the rules and their history; only admins and owners switch them. */
@RestController
@RequestMapping("/api/tenants/{tenantId}/automations")
@RequiredArgsConstructor
public class AutomationController {

	public record ToggleRequest(@NotNull Boolean enabled) {
	}

	private final AutomationService automationService;

	@GetMapping
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public List<RuleView> rules(@PathVariable UUID tenantId) {
		return automationService.rules(tenantId);
	}

	@PutMapping("/{type}")
	@PreAuthorize("@tenantSecurity.hasRole(#tenantId, 'ADMIN', 'OWNER')")
	public List<RuleView> toggle(@PathVariable UUID tenantId, @PathVariable AutomationRuleType type,
			@AuthenticationPrincipal AuthenticatedUser user, @RequestBody @jakarta.validation.Valid ToggleRequest request) {
		return automationService.setEnabled(tenantId, type, request.enabled(), user.getId());
	}

	@GetMapping("/runs")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public PageResponse<RunView> runs(@PathVariable UUID tenantId, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return automationService.runs(tenantId, page, size);
	}
}
