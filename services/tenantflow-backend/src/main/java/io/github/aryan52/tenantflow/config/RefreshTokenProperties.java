package io.github.aryan52.tenantflow.config;

import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "tenantflow.refresh-token")
public record RefreshTokenProperties(
		@Positive long expirationDays
) {
	public Duration expiration() {
		return Duration.ofDays(expirationDays);
	}
}
