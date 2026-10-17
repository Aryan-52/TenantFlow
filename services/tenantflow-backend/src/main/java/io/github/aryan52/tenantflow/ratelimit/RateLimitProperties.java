package io.github.aryan52.tenantflow.ratelimit;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code tenantflow.rate-limit.*}. Each rule is {@code limit} requests per {@code window},
 * counted per client IP (unauthenticated endpoints) or per user (authenticated ones).
 * Rule names: login, register, forgot-password, reset-password, refresh, upload, search.
 */
@ConfigurationProperties(prefix = "tenantflow.rate-limit")
public record RateLimitProperties(
		boolean enabled,
		Store store,
		Map<String, Rule> rules
) {
	public enum Store { REDIS, MEMORY }

	public record Rule(int limit, Duration window) {
	}

	public RateLimitProperties {
		store = store == null ? Store.REDIS : store;
		rules = rules == null ? Map.of() : Map.copyOf(rules);
	}
}
