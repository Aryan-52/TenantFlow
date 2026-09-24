package io.github.aryan52.tenantflow.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Shared helper for anything that hands a raw secret token to a client (password reset
 * links, refresh-token cookies) while only ever persisting a hash of it. Used by
 * RefreshTokenService and PasswordResetService.
 */
public final class SecureTokens {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private static final int TOKEN_BYTES = 32;

	private SecureTokens() {
	}

	/** A cryptographically random, URL-safe token (256 bits of entropy). */
	public static String generate() {
		byte[] bytes = new byte[TOKEN_BYTES];
		SECURE_RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** SHA-256 digest of the given raw token, base64-encoded. This - never the raw token -
	 * is what gets persisted. */
	public static String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(hashed);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}
}
