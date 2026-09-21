package io.github.aryan52.tenantflow.security;

import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.Role;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("tenantSecurity")
@RequiredArgsConstructor
public class TenantSecurity {

	private final MembershipRepository membershipRepository;

	@Transactional(readOnly = true)
	public boolean hasAccess(UUID tenantId) {
		return getMembership(tenantId).isPresent();
	}

	@Transactional(readOnly = true)
	public boolean hasRole(UUID tenantId, String... roles) {
		return getMembership(tenantId)
				.map(Membership::getRole)
				.map(role -> Arrays.stream(roles).anyMatch(r -> role.name().equals(r)))
				.orElse(false);
	}

	private Optional<Membership> getMembership(UUID tenantId) {
		if (tenantId == null) {
			return Optional.empty();
		}

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
			return Optional.empty();
		}

		return membershipRepository.findByUser_IdAndTenant_Id(user.getId(), tenantId);
	}
}
