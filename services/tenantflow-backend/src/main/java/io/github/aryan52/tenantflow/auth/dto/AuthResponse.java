package io.github.aryan52.tenantflow.auth.dto;

import java.util.UUID;

public record AuthResponse(
		String token,
		String tokenType,
		long expiresInSeconds,
		UUID userId,
		String email,
		String name
) {

	public static AuthResponse bearer(String token, long expiresInSeconds, UUID userId, String email, String name) {
		return new AuthResponse(token, "Bearer", expiresInSeconds, userId, email, name);
	}
}
