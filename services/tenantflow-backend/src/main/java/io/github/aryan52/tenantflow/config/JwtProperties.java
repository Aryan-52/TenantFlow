package io.github.aryan52.tenantflow.config;

import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "tenantflow.jwt")
public record JwtProperties(
		String secret,
		@Positive long expirationSeconds
) {

	static final int MIN_SECRET_LENGTH = 32;

	/**
	 * Checked here rather than with {@code @NotBlank @Size}: Spring's validation report prints
	 * the rejected value, and this value is a secret. These messages name the setting and the
	 * rule, never the value.
	 */
	public JwtProperties {
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException("TENANTFLOW_JWT_SECRET is not set. Set it to a random value of at least "
					+ MIN_SECRET_LENGTH + " characters, as an environment variable or in the git-ignored .env file "
					+ "at the repository root (see docs/OPERATIONS.md, \"Local development\").");
		}
		if (secret.length() < MIN_SECRET_LENGTH) {
			throw new IllegalStateException("TENANTFLOW_JWT_SECRET is too short: it must be at least "
					+ MIN_SECRET_LENGTH + " characters (generate one with: openssl rand -base64 48).");
		}
	}

	public Duration expiration() {
		return Duration.ofSeconds(expirationSeconds);
	}
}
