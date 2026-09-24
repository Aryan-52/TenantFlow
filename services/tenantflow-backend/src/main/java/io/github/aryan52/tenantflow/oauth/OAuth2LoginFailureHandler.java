package io.github.aryan52.tenantflow.oauth;

import io.github.aryan52.tenantflow.config.CorsProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/** Sends the browser back to the login page with a generic error flag - never leaks
 * why the OAuth attempt failed (e.g. that a specific email was unverified). */
@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

	private final CorsProperties corsProperties;

	@Override
	public void onAuthenticationFailure(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticationException exception
	) throws IOException, ServletException {
		String frontendOrigin = corsProperties.allowedOrigins().isEmpty()
				? "/"
				: corsProperties.allowedOrigins().get(0);

		String redirectUrl = UriComponentsBuilder.fromUriString(frontendOrigin)
				.path("/login")
				.queryParam("error", "oauth_failed")
				.build()
				.toUriString();
		response.sendRedirect(redirectUrl);
	}
}
