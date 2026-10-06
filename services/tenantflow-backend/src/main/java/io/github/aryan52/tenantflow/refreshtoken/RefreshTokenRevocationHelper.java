package io.github.aryan52.tenantflow.refreshtoken;

import io.github.aryan52.tenantflow.entity.RefreshToken;
import io.github.aryan52.tenantflow.repository.RefreshTokenRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolated transactional boundary for session-family revocation.
 *
 * <p>This exists as a separate Spring bean so that {@link RefreshTokenService#revokeAllForUser}
 * can be called with {@link Propagation#REQUIRES_NEW} from within {@code rotate()}, which is
 * itself {@code @Transactional}. Spring's proxy-based AOP cannot apply transaction advice to
 * internal self-invocations within the same bean - by extracting this into its own
 * {@code @Component}, the {@code REQUIRES_NEW} boundary is honoured correctly.
 *
 * <p>Without {@code REQUIRES_NEW}, the revocation performed during reuse-detection would be
 * rolled back when {@code rotate()} throws {@link InvalidRefreshTokenException}, silently
 * leaving the stolen session active.
 */
@Component
@RequiredArgsConstructor
class RefreshTokenRevocationHelper {

	private final RefreshTokenRepository refreshTokenRepository;

	/** Revokes every active refresh token for the given user in its own committed transaction. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void revokeAllForUser(UUID userId) {
		List<RefreshToken> active = refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId);
		Instant now = Instant.now();
		active.forEach(t -> t.setRevokedAt(now));
		refreshTokenRepository.saveAll(active);
	}
}
