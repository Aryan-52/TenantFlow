package io.github.aryan52.tenantflow.user;

import io.github.aryan52.tenantflow.auth.dto.AuthResponse;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.error.ResourceNotFoundException;
import io.github.aryan52.tenantflow.refreshtoken.RefreshTokenService;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.security.JwtService;
import io.github.aryan52.tenantflow.user.dto.ChangePasswordRequest;
import io.github.aryan52.tenantflow.user.dto.UpdateProfileRequest;
import io.github.aryan52.tenantflow.user.dto.UserProfileResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

	private static final Logger log = LoggerFactory.getLogger(UserService.class);

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;

	@Transactional(readOnly = true)
	public UserProfileResponse getProfile(UUID userId) {
		return UserProfileResponse.from(findUser(userId));
	}

	@Transactional
	public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
		User user = findUser(userId);
		user.setName(request.name().trim());
		return UserProfileResponse.from(userRepository.save(user));
	}

	/**
	 * Changes the current user's password and returns a freshly-issued access token.
	 *
	 * This bumps the user's token_version, which invalidates every access token issued
	 * before this call - including, in principle, the one used to call this endpoint. The
	 * caller (the same browser tab that just submitted the form) must keep working though,
	 * so we hand back a brand new token generated *after* the version bump, which the
	 * frontend swaps in immediately. Every other device/tab is signed out on its next
	 * request, and any "remember me" persistent sessions are revoked outright.
	 *
	 * Deliberately logged at each decision point (outcome only - never the password or its
	 * hash) so a production incident here is diagnosable from logs alone.
	 */
	@Transactional
	public AuthResponse changePassword(UUID userId, ChangePasswordRequest request) {
		User user = findUser(userId);

		boolean currentMatches = passwordEncoder.matches(request.currentPassword(), user.getPasswordHash());
		if (!currentMatches) {
			log.info("Password change rejected for user {}: current password did not match", userId);
			throw new InvalidCurrentPasswordException();
		}

		if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
			log.info("Password change rejected for user {}: new password equals current password", userId);
			throw new PasswordUnchangedException();
		}

		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		user.setTokenVersion(user.getTokenVersion() + 1);
		User saved = userRepository.save(user);

		refreshTokenService.revokeAllForUser(saved.getId());

		log.info("Password changed successfully for user {}; token_version is now {}", userId, saved.getTokenVersion());

		return AuthResponse.bearer(
				jwtService.generateToken(saved),
				jwtService.getExpirationSeconds(),
				saved.getId(),
				saved.getEmail(),
				saved.getName()
		);
	}

	private User findUser(UUID userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User"));
	}
}
