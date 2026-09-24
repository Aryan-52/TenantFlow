package io.github.aryan52.tenantflow.passwordreset;

import io.github.aryan52.tenantflow.config.PasswordResetProperties;
import io.github.aryan52.tenantflow.email.EmailService;
import io.github.aryan52.tenantflow.entity.PasswordResetToken;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.refreshtoken.RefreshTokenService;
import io.github.aryan52.tenantflow.repository.PasswordResetTokenRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.security.SecureTokens;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the forgot-password / reset-password flow. Two rules matter everywhere in
 * this class: never reveal whether an email address has an account, and never persist
 * a raw reset token - only its SHA-256 hash (see SecureTokens).
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

	private final UserRepository userRepository;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final PasswordResetProperties passwordResetProperties;
	private final EmailService emailService;
	private final RefreshTokenService refreshTokenService;

	/** Always completes the same way whether or not the email belongs to an account - the
	 * controller returns an identical generic response either way. */
	@Transactional
	public void requestReset(String email) {
		String normalized = normalizeEmail(email);
		userRepository.findByEmail(normalized).ifPresent(this::issueResetToken);
	}

	private void issueResetToken(User user) {
		// Invalidate any reset requests still outstanding for this user before issuing a
		// new one, so only the most recent link is ever valid.
		List<PasswordResetToken> outstanding = passwordResetTokenRepository.findByUserIdAndUsedAtIsNull(user.getId());
		Instant now = Instant.now();
		outstanding.forEach(t -> t.setUsedAt(now));
		passwordResetTokenRepository.saveAll(outstanding);

		String rawToken = SecureTokens.generate();
		PasswordResetToken token = PasswordResetToken.builder()
				.userId(user.getId())
				.tokenHash(SecureTokens.hash(rawToken))
				.expiresAt(now.plus(passwordResetProperties.expiration()))
				.build();
		passwordResetTokenRepository.save(token);

		emailService.sendPasswordResetEmail(user.getEmail(), passwordResetProperties.buildResetUrl(rawToken));
	}

	@Transactional
	public void resetPassword(String rawToken, String newPassword) {
		PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(SecureTokens.hash(rawToken))
				.orElseThrow(InvalidOrExpiredResetTokenException::new);

		// Same exception (and message) for "already used" and "expired" as for "not found" -
		// telling a caller *why* a token failed is itself information they shouldn't get.
		if (token.isUsed() || token.isExpired()) {
			throw new InvalidOrExpiredResetTokenException();
		}

		User user = userRepository.findById(token.getUserId())
				.orElseThrow(InvalidOrExpiredResetTokenException::new);

		user.setPasswordHash(passwordEncoder.encode(newPassword));
		user.setTokenVersion(user.getTokenVersion() + 1);
		userRepository.save(user);

		token.setUsedAt(Instant.now());
		passwordResetTokenRepository.save(token);

		// The user is about to be sent back to the login page, not kept in this session -
		// so, unlike a profile password change, there is no "current session" to preserve.
		// Sign out every device: existing access JWTs stop working (token_version bump) and
		// every "remember me" persistent session is revoked.
		refreshTokenService.revokeAllForUser(user.getId());
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
