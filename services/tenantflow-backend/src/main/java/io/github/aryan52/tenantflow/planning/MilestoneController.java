package io.github.aryan52.tenantflow.planning;

import io.github.aryan52.tenantflow.planning.MilestoneService.MilestoneRequest;
import io.github.aryan52.tenantflow.planning.MilestoneService.MilestoneResponse;
import io.github.aryan52.tenantflow.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Members plan milestones like tasks; deleting one is reserved to workspace admins. */
@RestController
@RequestMapping("/api/tenants/{tenantId}/projects/{projectId}/milestones")
@RequiredArgsConstructor
public class MilestoneController {

	private final MilestoneService milestoneService;

	@GetMapping
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public List<MilestoneResponse> list(@PathVariable UUID tenantId, @PathVariable UUID projectId) {
		return milestoneService.list(tenantId, projectId);
	}

	@PostMapping
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public ResponseEntity<MilestoneResponse> create(@PathVariable UUID tenantId, @PathVariable UUID projectId,
			@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody MilestoneRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(milestoneService.create(tenantId, projectId, user.getId(), request));
	}

	@PutMapping("/{milestoneId}")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public MilestoneResponse update(@PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID milestoneId,
			@Valid @RequestBody MilestoneRequest request) {
		return milestoneService.update(tenantId, projectId, milestoneId, request);
	}

	@DeleteMapping("/{milestoneId}")
	@PreAuthorize("@tenantSecurity.hasRole(#tenantId, 'ADMIN', 'OWNER')")
	public ResponseEntity<Void> delete(@PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID milestoneId) {
		milestoneService.delete(tenantId, projectId, milestoneId);
		return ResponseEntity.noContent().build();
	}
}
