package io.github.aryan52.tenantflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deliberately unvalidated (no @NotBlank): both fields are allowed to be blank, which is
 * how Google OAuth stays disabled in environments that haven't configured it. See
 * OAuthClientRegistrationConfig, which only registers the OAuth2 client when both are set. */
@ConfigurationProperties(prefix = "tenantflow.oauth.google")
public record GoogleOAuthProperties(
		String clientId,
		String clientSecret,
		String frontendRedirectUri,
		String redirectUri
) {

	/** Spring Security's default: built from the request's scheme, host and port. */
	public static final String DEFAULT_REDIRECT_URI = "{baseUrl}/login/oauth2/code/{registrationId}";

	/**
	 * {@code redirectUri}: the callback Google sends the browser back to. Blank = Spring's
	 * default, derived from the incoming request (right for local development and for a backend
	 * on its own domain). Behind a proxy that serves the API on the frontend's domain (Vercel
	 * rewrites), set it to that public address, e.g. https://app.example.com/login/oauth2/code/google,
	 * so it never depends on how forwarded headers survive each proxy hop. It must also be
	 * registered as an authorised redirect URI in Google Cloud.
	 */
	public GoogleOAuthProperties {
		redirectUri = redirectUri == null || redirectUri.isBlank() ? DEFAULT_REDIRECT_URI : redirectUri.trim();
	}
	public boolean isConfigured() {
		return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
	}
}
