package io.github.aryan52.tenantflow.auth;

import io.github.aryan52.tenantflow.audit.AuditAction;
import io.github.aryan52.tenantflow.audit.AuditEvent.Outcome;
import io.github.aryan52.tenantflow.audit.AuditService;
import io.github.aryan52.tenantflow.audit.AuditService.Entry;
import io.github.aryan52.tenantflow.auth.dto.AuthResponse;
import io.github.aryan52.tenantflow.auth.dto.LoginRequest;
import io.github.aryan52.tenantflow.auth.dto.RegisterRequest;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.refreshtoken.RefreshTokenService;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.security.JwtService;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;
	private final AuditService auditService;

	/**
	 * A real BCrypt hash of a random value, compared against when the email is unknown so
	 * that "no such account" takes as long as "wrong password" (no account enumeration by
	 * response timing).
	 */
	private volatile String dummyHash;

	/** @param refreshToken the raw "remember me" refresh token to set as an HttpOnly
	 * cookie, or null when the login did not request a persistent session. */
	public record LoginResult(AuthResponse response, String refreshToken) {
	}

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		String email = normalizeEmail(request.email());
		if (userRepository.existsByEmail(email)) {
			throw new DuplicateEmailException();
		}

		User user = User.builder()
				.email(email)
				.passwordHash(passwordEncoder.encode(request.password()))
				.name(request.name().trim())
				.build();

		try {
			User savedUser = userRepository.saveAndFlush(user);
			auditService.record(Entry.success(AuditAction.AUTH_REGISTERED, null, "USER", savedUser.getId(), null),
					savedUser.getId(), savedUser.getEmail());
			return AuthResponse.bearer(
					jwtService.generateToken(savedUser),
					jwtService.getExpirationSeconds(),
					savedUser.getId(),
					savedUser.getEmail(),
					savedUser.getName()
			);
		} catch (DataIntegrityViolationException ex) {
			throw new DuplicateEmailException();
		}
	}

	@Transactional
	public LoginResult login(LoginRequest request) {
		String email = normalizeEmail(request.email());
		User user = userRepository.findByEmail(email).orElse(null);
		if (user == null) {
			passwordEncoder.matches(request.password(), dummyHash());
			throw loginFailed(null, email);
		}
		if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw loginFailed(user.getId(), email);
		}
		auditService.record(Entry.success(AuditAction.AUTH_LOGIN_SUCCEEDED, null, "USER", user.getId(),
				request.rememberMeRequested() ? "rememberMe=true" : null), user.getId(), user.getEmail());

		AuthResponse response = AuthResponse.bearer(
				jwtService.generateToken(user),
				jwtService.getExpirationSeconds(),
				user.getId(),
				user.getEmail(),
				user.getName()
		);

		String refreshToken = request.rememberMeRequested() ? refreshTokenService.issue(user) : null;
		return new LoginResult(response, refreshToken);
	}

	private InvalidCredentialsException loginFailed(java.util.UUID userId, String email) {
		auditService.recordIndependently(Entry.of(AuditAction.AUTH_LOGIN_FAILED, Outcome.FAILURE, null, "USER", userId,
				userId == null ? "unknown account" : "bad credentials"), userId, email);
		return new InvalidCredentialsException();
	}

	private String dummyHash() {
		String hash = dummyHash;
		if (hash == null) {
			hash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
			dummyHash = hash;
		}
		return hash;
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
