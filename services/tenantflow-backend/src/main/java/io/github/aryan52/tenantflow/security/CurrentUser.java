package io.github.aryan52.tenantflow.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Access to the authenticated principal outside of controller method arguments. */
public final class CurrentUser {

	private CurrentUser() {
	}

	public static Optional<AuthenticatedUser> get() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.isAuthenticated()
				&& authentication.getPrincipal() instanceof AuthenticatedUser user) {
			return Optional.of(user);
		}
		return Optional.empty();
	}

	/** @throws IllegalStateException when called outside an authenticated request. */
	public static UUID id() {
		return get().map(AuthenticatedUser::getId)
				.orElseThrow(() -> new IllegalStateException("No authenticated user"));
	}
}
