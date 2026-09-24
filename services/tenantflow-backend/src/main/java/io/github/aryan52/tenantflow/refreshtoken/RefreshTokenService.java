package io.github.aryan52.tenantflow.refreshtoken;

import io.github.aryan52.tenantflow.config.RefreshTokenProperties;
import io.github.aryan52.tenantflow.entity.RefreshToken;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.repository.RefreshTokenRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.security.SecureTokens;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs "Remember me". Only a SHA-256 hash of the raw token is ever persisted; the raw
 * value lives solely in the HttpOnly refresh cookie (see RefreshCookieUtil) and this
 * class's return values.
 *
 * Rotation: every successful refresh revokes the presented token and issues a new one.
 * Reuse detection: presenting an already-revoked (but not yet expired) token is treated
 * as a signal of token theft - the entire session family for that user is revoked.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

	private final RefreshTokenRepository refreshTokenRepository;
	private final UserRepository userRepository;
	private final RefreshTokenProperties refreshTokenProperties;

	public record RotationResult(User user, String rawToken) {
	}

	@Transactional
	public String issue(User user) {
		String rawToken = SecureTokens.generate();
		RefreshToken token = RefreshToken.builder()
				.userId(user.getId())
				.tokenHash(SecureTokens.hash(rawToken))
				.expiresAt(Instant.now().plus(refreshTokenProperties.expiration()))
				.build();
		refreshTokenRepository.save(token);
		return rawToken;
	}

	@Transactional
	public RotationResult rotate(String rawToken) {
		RefreshToken existing = refreshTokenRepository.findByTokenHash(SecureTokens.hash(rawToken))
				.orElseThrow(InvalidRefreshTokenException::new);

		if (existing.isRevoked()) {
			// A revoked token being presented again means it was copied/stolen before (or
			// after) rotation. Kill the whole session family for this user rather than just
			// this one token.
			revokeAllForUser(existing.getUserId());
			throw new InvalidRefreshTokenException();
		}

		if (existing.isExpired()) {
			throw new InvalidRefreshTokenException();
		}

		User user = userRepository.findById(existing.getUserId())
				.orElseThrow(InvalidRefreshTokenException::new);

		existing.setRevokedAt(Instant.now());
		refreshTokenRepository.save(existing);

		String newRawToken = issue(user);
		return new RotationResult(user, newRawToken);
	}

	@Transactional
	public void revoke(String rawToken) {
		refreshTokenRepository.findByTokenHash(SecureTokens.hash(rawToken))
				.filter(t -> !t.isRevoked())
				.ifPresent(t -> {
					t.setRevokedAt(Instant.now());
					refreshTokenRepository.save(t);
				});
	}

	/** Revokes every active persistent session for a user - called on password change/reset
	 * so a compromised or old device's "remember me" session stops working immediately. */
	@Transactional
	public void revokeAllForUser(java.util.UUID userId) {
		List<RefreshToken> active = refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId);
		Instant now = Instant.now();
		active.forEach(t -> t.setRevokedAt(now));
		refreshTokenRepository.saveAll(active);
	}
}
