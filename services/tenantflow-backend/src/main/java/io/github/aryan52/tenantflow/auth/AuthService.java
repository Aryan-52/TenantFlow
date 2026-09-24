package io.github.aryan52.tenantflow.auth;

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
			User savedUser = userRepository.save(user);
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
		User user = userRepository.findByEmail(normalizeEmail(request.email()))
				.orElseThrow(InvalidCredentialsException::new);

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}

		AuthResponse response = AuthResponse.bearer(
				jwtService.generateToken(user),
				jwtService.getExpirationSeconds(),
				user.getId(),
				user.getEmail(),
				user.getName()
		);

		String refreshToken = request.rememberMe() ? refreshTokenService.issue(user) : null;
		return new LoginResult(response, refreshToken);
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
