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
		Instant joinedAt,
		/** Open tasks assigned to this member in the workspace (list responses only, else null). */
		Long openTasks,
		/** Of those, how many are past their due date (list responses only, else null). */
		Long overdueTasks
) {
	public static MembershipResponse from(Membership membership) {
		return from(membership, null, null);
	}

	public static MembershipResponse from(Membership membership, Long openTasks, Long overdueTasks) {
		return new MembershipResponse(
				membership.getId(),
				membership.getUser().getId(),
				membership.getUser().getEmail(),
				membership.getUser().getName(),
				membership.getRole(),
				membership.getCreatedAt(),
				openTasks,
				overdueTasks
		);
	}
}
