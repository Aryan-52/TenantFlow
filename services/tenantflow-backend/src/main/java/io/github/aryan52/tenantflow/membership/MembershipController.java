package io.github.aryan52.tenantflow.membership;

import io.github.aryan52.tenantflow.membership.dto.AddMembershipRequest;
import io.github.aryan52.tenantflow.membership.dto.MembershipResponse;
import io.github.aryan52.tenantflow.membership.dto.UpdateMembershipRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/{tenantId}/members")
@RequiredArgsConstructor
public class MembershipController {

	private final MembershipService membershipService;

	@GetMapping
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public List<MembershipResponse> getMemberships(@PathVariable UUID tenantId) {
		return membershipService.getMemberships(tenantId);
	}

	@PostMapping
	@PreAuthorize("@tenantSecurity.hasRole(#tenantId, 'ADMIN', 'OWNER')")
	public ResponseEntity<MembershipResponse> addMembership(
			@PathVariable UUID tenantId,
			@Valid @RequestBody AddMembershipRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(membershipService.addMembership(tenantId, request));
	}

	@PutMapping("/{membershipId}")
	@PreAuthorize("@tenantSecurity.hasRole(#tenantId, 'ADMIN', 'OWNER')")
	public MembershipResponse updateMembership(
			@PathVariable UUID tenantId,
			@PathVariable UUID membershipId,
			@Valid @RequestBody UpdateMembershipRequest request
	) {
		return membershipService.updateMembership(tenantId, membershipId, request);
	}

	@DeleteMapping("/{membershipId}")
	@PreAuthorize("@tenantSecurity.hasRole(#tenantId, 'ADMIN', 'OWNER')")
	public ResponseEntity<Void> removeMembership(
			@PathVariable UUID tenantId,
			@PathVariable UUID membershipId
	) {
		membershipService.removeMembership(tenantId, membershipId);
		return ResponseEntity.noContent().build();
	}
}
