package io.github.aryan52.tenantflow.user.dto;

import io.github.aryan52.tenantflow.entity.User;
import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
		UUID id,
		String email,
		String name,
		Instant createdAt
) {
	public static UserProfileResponse from(User user) {
		return new UserProfileResponse(
				user.getId(),
				user.getEmail(),
				user.getName(),
				user.getCreatedAt()
		);
	}
}
