package io.github.aryan52.tenantflow.auth;

import io.github.aryan52.tenantflow.auth.dto.AuthResponse;
import io.github.aryan52.tenantflow.auth.dto.LoginRequest;
import io.github.aryan52.tenantflow.auth.dto.RegisterRequest;
import io.github.aryan52.tenantflow.entity.User;
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

	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(normalizeEmail(request.email()))
				.orElseThrow(InvalidCredentialsException::new);

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}

		return AuthResponse.bearer(
				jwtService.generateToken(user),
				jwtService.getExpirationSeconds(),
				user.getId(),
				user.getEmail(),
				user.getName()
		);
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
