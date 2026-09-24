package io.github.aryan52.tenantflow.oauth;

import io.github.aryan52.tenantflow.config.GoogleOAuthProperties;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.refreshtoken.RefreshCookieUtil;
import io.github.aryan52.tenantflow.refreshtoken.RefreshTokenService;
import io.github.aryan52.tenantflow.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Runs once Spring Security has completed the Google OAuth2/OIDC handshake and
 * GoogleUserProvisioningService has ensured a matching local User exists.
 *
 * We deliberately never put our own access JWT in the redirect URL (URLs end up in
 * browser history and server access logs). Instead we set the same HttpOnly refresh
 * cookie "remember me" uses, then send the browser to a lightweight frontend page that
 * calls POST /api/auth/refresh to mint an access token from that cookie - reusing the
 * exact same, already-audited path a returning "remember me" user goes through.
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

	private final UserRepository userRepository;
	private final RefreshTokenService refreshTokenService;
	private final RefreshCookieUtil refreshCookieUtil;
	private final GoogleOAuthProperties googleOAuthProperties;

	@Override
	public void onAuthenticationSuccess(
			HttpServletRequest request,
			HttpServletResponse response,
			Authentication authentication
	) throws IOException, ServletException {
		OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
		String email = oidcUser.getEmail().trim().toLowerCase(Locale.ROOT);

		User user = userRepository.findByEmail(email).orElse(null);
		if (user == null) {
			// Should not happen - GoogleUserProvisioningService creates the user during the
			// same request chain - but fail closed rather than send the browser anywhere.
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Account provisioning failed");
			return;
		}

		String rawRefreshToken = refreshTokenService.issue(user);
		response.addHeader(HttpHeaders.SET_COOKIE, refreshCookieUtil.build(rawRefreshToken).toString());

		String redirectUrl = UriComponentsBuilder.fromUriString(googleOAuthProperties.frontendRedirectUri())
				.build()
				.toUriString();
		response.sendRedirect(redirectUrl);
	}
}
