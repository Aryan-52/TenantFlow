package io.github.aryan52.tenantflow.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "tenantflow.jwt")
public record JwtProperties(
		@NotBlank @Size(min = 32) String secret,
		@Positive long expirationSeconds
) {

	public Duration expiration() {
		return Duration.ofSeconds(expirationSeconds);
	}
}
