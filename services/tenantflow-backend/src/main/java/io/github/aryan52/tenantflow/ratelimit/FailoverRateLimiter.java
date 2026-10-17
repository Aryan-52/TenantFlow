package io.github.aryan52.tenantflow.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses the shared (Redis) limiter and falls back to the per-instance limiter when Redis is
 * unavailable. Rate limiting is a security control, so a Redis outage degrades it to
 * per-instance enforcement - it never turns it off.
 */
public class FailoverRateLimiter implements RateLimiter {

	private static final Logger log = LoggerFactory.getLogger(FailoverRateLimiter.class);
	private static final long WARN_INTERVAL_MILLIS = 60_000;

	private final RateLimiter primary;
	private final RateLimiter fallback;
	private final Counter fallbackCounter;
	private final AtomicLong lastWarning = new AtomicLong();

	public FailoverRateLimiter(RateLimiter primary, RateLimiter fallback, MeterRegistry meterRegistry) {
		this.primary = primary;
		this.fallback = fallback;
		this.fallbackCounter = Counter.builder("tenantflow.ratelimit.fallback")
				.description("Rate-limit decisions made by the in-memory fallback because Redis was unavailable")
				.register(meterRegistry);
	}

	@Override
	public Decision tryConsume(String key, int limit, Duration window) {
		try {
			return primary.tryConsume(key, limit, window);
		} catch (RuntimeException ex) {
			fallbackCounter.increment();
			long now = System.currentTimeMillis();
			long last = lastWarning.get();
			if (now - last > WARN_INTERVAL_MILLIS && lastWarning.compareAndSet(last, now)) {
				log.warn("Redis rate limiter unavailable ({}); enforcing limits per instance until it recovers",
						ex.getClass().getSimpleName());
			}
			return fallback.tryConsume(key, limit, window);
		}
	}
}
