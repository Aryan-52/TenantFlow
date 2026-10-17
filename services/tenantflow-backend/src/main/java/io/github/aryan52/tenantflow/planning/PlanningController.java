package io.github.aryan52.tenantflow.planning;

import io.github.aryan52.tenantflow.planning.MilestoneService.MilestoneResponse;
import io.github.aryan52.tenantflow.planning.PlanningService.Calendar;
import io.github.aryan52.tenantflow.planning.PlanningService.CalendarScope;
import io.github.aryan52.tenantflow.planning.PlanningService.MemberOverview;
import io.github.aryan52.tenantflow.planning.PlanningService.PortfolioRow;
import io.github.aryan52.tenantflow.planning.PlanningService.Workload;
import io.github.aryan52.tenantflow.security.AuthenticatedUser;
import io.github.aryan52.tenantflow.security.TenantSecurity;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/{tenantId}")
@RequiredArgsConstructor
public class PlanningController {

	public record CapacityRequest(Integer weeklyCapacity) {
	}

	private final PlanningService planningService;
	private final MilestoneService milestoneService;
	private final TenantSecurity tenantSecurity;

	/** scope=mine (my tasks) or workspace (everything); optional projectId narrows to one project. */
	@GetMapping("/calendar")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public Calendar calendar(@PathVariable UUID tenantId, @AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "workspace") String scope,
			@RequestParam(required = false) UUID projectId) {
		CalendarScope s = switch (scope.toLowerCase(Locale.ROOT)) {
			case "mine" -> CalendarScope.MINE;
			case "workspace" -> CalendarScope.WORKSPACE;
			default -> throw new IllegalArgumentException("scope must be 'mine' or 'workspace'");
		};
		return planningService.calendar(tenantId, user.getId(), from, to, s, projectId);
	}

	@GetMapping("/portfolio")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public List<PortfolioRow> portfolio(@PathVariable UUID tenantId) {
		return planningService.portfolio(tenantId);
	}

	@GetMapping("/milestones")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public List<MilestoneResponse> milestones(@PathVariable UUID tenantId) {
		return milestoneService.listAllForTenant(tenantId);
	}

	@GetMapping("/workload")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public Workload workload(@PathVariable UUID tenantId, @AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(defaultValue = "6") int weeks) {
		return planningService.workload(tenantId, user.getId(), weeks);
	}

	/** Admins set anyone's capacity; members only their own. */
	@PutMapping("/members/{memberId}/capacity")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public Workload capacity(@PathVariable UUID tenantId, @PathVariable UUID memberId,
			@AuthenticationPrincipal AuthenticatedUser user, @RequestBody CapacityRequest request) {
		return planningService.setCapacity(tenantId, user.getId(), memberId, request.weeklyCapacity(),
				tenantSecurity.hasRole(tenantId, "ADMIN", "OWNER"));
	}

	@GetMapping("/members/{memberId}/overview")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public MemberOverview member(@PathVariable UUID tenantId, @PathVariable UUID memberId) {
		return planningService.member(tenantId, memberId);
	}
}
