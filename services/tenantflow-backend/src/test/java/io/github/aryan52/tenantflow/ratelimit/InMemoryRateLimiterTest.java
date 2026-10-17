package io.github.aryan52.tenantflow.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTest {

	private final AtomicLong now = new AtomicLong(1_000_000);
	private final Clock clock = new Clock() {
		@Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(java.time.ZoneId zone) { return this; }
		@Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
	};

	@Test
	void allowsUpToTheLimitThenDeniesUntilTheWindowResets() {
		InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);
		for (int i = 0; i < 3; i++) {
			assertThat(limiter.tryConsume("k", 3, Duration.ofSeconds(60)).allowed()).isTrue();
		}
		RateLimiter.Decision denied = limiter.tryConsume("k", 3, Duration.ofSeconds(60));
		assertThat(denied.allowed()).isFalse();
		assertThat(denied.count()).isEqualTo(4);
		assertThat(denied.retryAfterSecs()).isEqualTo(60);

		now.addAndGet(30_000);
		assertThat(limiter.tryConsume("k", 3, Duration.ofSeconds(60)).retryAfterSecs()).isEqualTo(30);

		now.addAndGet(30_000);
		assertThat(limiter.tryConsume("k", 3, Duration.ofSeconds(60)).allowed()).isTrue();
	}

	@Test
	void keysAreIndependent() {
		InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);
		limiter.tryConsume("a", 1, Duration.ofMinutes(1));
		assertThat(limiter.tryConsume("a", 1, Duration.ofMinutes(1)).allowed()).isFalse();
		assertThat(limiter.tryConsume("b", 1, Duration.ofMinutes(1)).allowed()).isTrue();
	}

	@Test
	void memoryIsBoundedUnderAKeyFlood() {
		InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);
		for (int i = 0; i < InMemoryRateLimiter.MAX_KEYS + 10; i++) {
			limiter.tryConsume("flood-" + i, 5, Duration.ofMinutes(1));
		}
		assertThat(limiter.size()).isLessThanOrEqualTo(InMemoryRateLimiter.MAX_KEYS);
	}

	@Test
	void failoverUsesTheFallbackWhenThePrimaryThrows() {
		var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
		RateLimiter broken = (k, l, w) -> { throw new IllegalStateException("redis down"); };
		FailoverRateLimiter limiter = new FailoverRateLimiter(broken, new InMemoryRateLimiter(clock), registry);

		assertThat(limiter.tryConsume("k", 1, Duration.ofMinutes(1)).allowed()).isTrue();
		// Still enforced - the outage must not disable the limit.
		assertThat(limiter.tryConsume("k", 1, Duration.ofMinutes(1)).allowed()).isFalse();
		assertThat(registry.counter("tenantflow.ratelimit.fallback").count()).isEqualTo(2);
	}
}
