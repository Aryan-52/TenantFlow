package io.github.aryan52.tenantflow.refreshtoken;

import io.github.aryan52.tenantflow.config.CookieProperties;
import io.github.aryan52.tenantflow.config.RefreshTokenProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshCookieUtil {

	public static final String COOKIE_NAME = "tenantflow_refresh";

	private final CookieProperties cookieProperties;
	private final RefreshTokenProperties refreshTokenProperties;

	public ResponseCookie build(String rawToken) {
		return baseCookie(rawToken)
				.maxAge(refreshTokenProperties.expiration())
				.build();
	}

	/** An immediately-expiring cookie that clears the persistent session client-side (used
	 * on logout and when a refresh attempt fails). */
	public ResponseCookie clear() {
		return baseCookie("")
				.maxAge(0)
				.build();
	}

	private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
		return ResponseCookie.from(COOKIE_NAME, value)
				.httpOnly(true)
				.secure(cookieProperties.secure())
				.sameSite(cookieProperties.sameSite())
				.path("/api/auth");
	}
}
