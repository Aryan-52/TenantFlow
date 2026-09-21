package io.github.aryan52.tenantflow.tenant;

import io.github.aryan52.tenantflow.security.AuthenticatedUser;
import io.github.aryan52.tenantflow.tenant.dto.CreateTenantRequest;
import io.github.aryan52.tenantflow.tenant.dto.TenantResponse;
import io.github.aryan52.tenantflow.tenant.dto.UpdateTenantRequest;
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

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

	private final TenantService tenantService;

	@PostMapping
	public ResponseEntity<TenantResponse> createTenant(
			@Valid @RequestBody CreateTenantRequest request,
			@AuthenticationPrincipal AuthenticatedUser currentUser
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(tenantService.createTenant(request, currentUser.getId()));
	}

	@GetMapping
	public List<TenantResponse> getUserTenants(@AuthenticationPrincipal AuthenticatedUser currentUser) {
		return tenantService.getUserTenants(currentUser.getId());
	}

	@GetMapping("/{tenantId}")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public TenantResponse getTenant(
			@PathVariable UUID tenantId,
			@AuthenticationPrincipal AuthenticatedUser currentUser
	) {
		return tenantService.getTenant(tenantId, currentUser.getId());
	}

	@PutMapping("/{tenantId}")
	@PreAuthorize("@tenantSecurity.hasRole(#tenantId, 'ADMIN', 'OWNER')")
	public TenantResponse updateTenant(
			@PathVariable UUID tenantId,
			@Valid @RequestBody UpdateTenantRequest request,
			@AuthenticationPrincipal AuthenticatedUser currentUser
	) {
		return tenantService.updateTenant(tenantId, request, currentUser.getId());
	}

	@DeleteMapping("/{tenantId}")
	@PreAuthorize("@tenantSecurity.hasRole(#tenantId, 'OWNER')")
	public ResponseEntity<Void> deleteTenant(@PathVariable UUID tenantId) {
		tenantService.deleteTenant(tenantId);
		return ResponseEntity.noContent().build();
	}
}
