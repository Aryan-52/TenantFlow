package io.github.aryan52.tenantflow.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Controls how the refresh-token cookie is issued. `secure` must be true in any real
 * (HTTPS) deployment; it is only ever set to false for the bundled plain-HTTP local
 * Docker Compose stack. */
@Validated
@ConfigurationProperties(prefix = "tenantflow.cookie")
public record CookieProperties(
		boolean secure,
		@NotBlank String sameSite
) {
}
