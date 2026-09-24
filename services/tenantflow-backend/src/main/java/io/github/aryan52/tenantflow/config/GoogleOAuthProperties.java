package io.github.aryan52.tenantflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deliberately unvalidated (no @NotBlank): both fields are allowed to be blank, which is
 * how Google OAuth stays disabled in environments that haven't configured it. See
 * OAuthClientRegistrationConfig, which only registers the OAuth2 client when both are set. */
@ConfigurationProperties(prefix = "tenantflow.oauth.google")
public record GoogleOAuthProperties(
		String clientId,
		String clientSecret,
		String frontendRedirectUri
) {
	public boolean isConfigured() {
		return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
	}
}
