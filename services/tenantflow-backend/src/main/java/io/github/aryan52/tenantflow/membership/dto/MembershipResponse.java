package io.github.aryan52.tenantflow.membership.dto;

import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.Role;
import java.time.Instant;
import java.util.UUID;

public record MembershipResponse(
		UUID id,
		UUID userId,
		String email,
		String name,
		Role role,
		Instant joinedAt
) {
	public static MembershipResponse from(Membership membership) {
		return new MembershipResponse(
				membership.getId(),
				membership.getUser().getId(),
				membership.getUser().getEmail(),
				membership.getUser().getName(),
				membership.getRole(),
				membership.getCreatedAt()
		);
	}
}
