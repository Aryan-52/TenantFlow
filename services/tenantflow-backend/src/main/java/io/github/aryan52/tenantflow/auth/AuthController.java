package io.github.aryan52.tenantflow.auth;

import io.github.aryan52.tenantflow.auth.dto.AuthResponse;
import io.github.aryan52.tenantflow.auth.dto.LoginRequest;
import io.github.aryan52.tenantflow.auth.dto.RegisterRequest;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.refreshtoken.InvalidRefreshTokenException;
import io.github.aryan52.tenantflow.refreshtoken.RefreshCookieUtil;
import io.github.aryan52.tenantflow.refreshtoken.RefreshTokenService;
import io.github.aryan52.tenantflow.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final RefreshTokenService refreshTokenService;
	private final RefreshCookieUtil refreshCookieUtil;
	private final JwtService jwtService;

	@PostMapping("/register")
	public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
	}

	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
		AuthService.LoginResult result = authService.login(request);
		if (result.refreshToken() != null) {
			setRefreshCookie(response, result.refreshToken());
		}
		return result.response();
	}

	/** Exchanges a valid "remember me" refresh cookie for a new access token, rotating the
	 * refresh token in the process. Requires no Authorization header - the whole point is
	 * to work when the access token has already expired. */
	@PostMapping("/refresh")
	public AuthResponse refresh(
			@CookieValue(name = RefreshCookieUtil.COOKIE_NAME, required = false) String refreshToken,
			HttpServletResponse response
	) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new InvalidRefreshTokenException();
		}

		try {
			RefreshTokenService.RotationResult result = refreshTokenService.rotate(refreshToken);
			setRefreshCookie(response, result.rawToken());

			User user = result.user();
			return AuthResponse.bearer(
					jwtService.generateToken(user),
					jwtService.getExpirationSeconds(),
					user.getId(),
					user.getEmail(),
					user.getName()
			);
		} catch (InvalidRefreshTokenException ex) {
			clearRefreshCookie(response);
			throw ex;
		}
	}

	/** Public by design: must work even when the caller's access token has already
	 * expired. Only ever acts on the refresh cookie, never on the Authorization header. */
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(
			@CookieValue(name = RefreshCookieUtil.COOKIE_NAME, required = false) String refreshToken,
			HttpServletResponse response
	) {
		if (refreshToken != null && !refreshToken.isBlank()) {
			refreshTokenService.revoke(refreshToken);
		}
		clearRefreshCookie(response);
		return ResponseEntity.noContent().build();
	}

	private void setRefreshCookie(HttpServletResponse response, String rawToken) {
		response.addHeader(HttpHeaders.SET_COOKIE, refreshCookieUtil.build(rawToken).toString());
	}

	private void clearRefreshCookie(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, refreshCookieUtil.clear().toString());
	}
}
