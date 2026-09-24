package io.github.aryan52.tenantflow.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "tenantflow.password-reset")
public record PasswordResetProperties(
		@Positive long expirationMinutes,
		@NotBlank String urlTemplate
) {
	public Duration expiration() {
		return Duration.ofMinutes(expirationMinutes);
	}

	/** Substitutes {token} in the configured template with the raw (unhashed) reset token. */
	public String buildResetUrl(String rawToken) {
		return urlTemplate.replace("{token}", rawToken);
	}
}
