package io.github.aryan52.tenantflow.security;

import io.github.aryan52.tenantflow.config.JwtProperties;
import io.github.aryan52.tenantflow.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtService {

	private static final String EMAIL_CLAIM = "email";
	private static final String VERSION_CLAIM = "ver";

	private final JwtProperties jwtProperties;

	public String generateToken(User user) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(jwtProperties.expiration());

		return Jwts.builder()
				.subject(user.getId().toString())
				.claim(EMAIL_CLAIM, user.getEmail())
				.claim(VERSION_CLAIM, user.getTokenVersion())
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiresAt))
				.signWith(signingKey(), Jwts.SIG.HS256)
				.compact();
	}

	public boolean isTokenValid(String token) {
		try {
			extractUserId(token);
			extractEmail(token);
			return true;
		} catch (JwtException | IllegalArgumentException ex) {
			return false;
		}
	}

	public UUID extractUserId(String token) {
		return UUID.fromString(extractAllClaims(token).getSubject());
	}

	public String extractEmail(String token) {
		return extractAllClaims(token).get(EMAIL_CLAIM, String.class);
	}

	/** The token_version the user had at the moment this JWT was issued. A missing claim
	 * (tokens issued before this field existed) is treated as version 0, matching the
	 * default value new/existing users start at - so upgrading never invalidates
	 * already-issued tokens by itself. */
	public int extractTokenVersion(String token) {
		Integer version = extractAllClaims(token).get(VERSION_CLAIM, Integer.class);
		return version != null ? version : 0;
	}

	public long getExpirationSeconds() {
		return jwtProperties.expirationSeconds();
	}

	private Claims extractAllClaims(String token) {
		return Jwts.parser()
				.verifyWith(signingKey())
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	private SecretKey signingKey() {
		return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
	}
}
