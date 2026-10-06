package io.github.aryan52.tenantflow.oauth;

import io.github.aryan52.tenantflow.config.GoogleOAuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Safety net for /oauth2/authorization/google specifically. When Google OAuth is
 * configured, Spring Security's own OAuth2AuthorizationRequestRedirectFilter recognizes
 * this path and handles it before the request ever reaches Spring MVC dispatch, so this
 * controller is simply never invoked. When it is NOT configured, no such filter is
 * registered (see SecurityConfig), so this becomes the actual handler for the path -
 * returning a clean, controlled response instead of whatever the servlet
 * container/Spring MVC would otherwise do with an unmapped path under that prefix.
 *
 * The frontend is expected to check GET /api/auth/oauth-providers and not link to this
 * path at all when it's disabled - this exists purely as defense in depth for anyone
 * who reaches the URL directly (a bookmark, a stale link, typing it in).
 */
@RestController
@RequiredArgsConstructor
public class OAuthFallbackController {

	private final GoogleOAuthProperties googleOAuthProperties;

	public record OAuthUnavailableResponse(String message) {
	}

	@GetMapping("/oauth2/authorization/google")
	public ResponseEntity<OAuthUnavailableResponse> googleUnavailable() {
		if (!googleOAuthProperties.isConfigured()) {
			// OAuth is intentionally disabled: the endpoint does not exist from the
			// application's perspective. 404 is the correct response (and what the test
			// expects) - there is simply no Google login path to use.
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(new OAuthUnavailableResponse("Google sign-in is not configured."));
		}
		// If this executes while OAuth IS configured, Spring Security's
		// OAuth2AuthorizationRequestRedirectFilter should have intercepted it first.
		// Something went wrong upstream - still respond safely rather than falling through.
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(new OAuthUnavailableResponse(
						"Google sign-in is temporarily unavailable. Please use your email and password, or try again shortly."));
	}
}
