package io.github.aryan52.tenantflow.ratelimit;

import io.github.aryan52.tenantflow.audit.AuditService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

	@Bean
	public RateLimiter rateLimiter(RateLimitProperties properties, ObjectProvider<StringRedisTemplate> redis,
			MeterRegistry meterRegistry) {
		InMemoryRateLimiter memory = new InMemoryRateLimiter();
		if (properties.store() == RateLimitProperties.Store.MEMORY) {
			return memory;
		}
		return new FailoverRateLimiter(new RedisRateLimiter(redis.getObject()), memory, meterRegistry);
	}

	@Bean
	public RateLimitFilter rateLimitFilter(RateLimitProperties properties, RateLimiter rateLimiter,
			AuditService auditService, MeterRegistry meterRegistry, JsonMapper jsonMapper) {
		return new RateLimitFilter(properties, rateLimiter, auditService, meterRegistry, jsonMapper);
	}

	/** Registered only inside the Spring Security chain (after JWT authentication), not twice. */
	@Bean
	public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
		FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}
}
