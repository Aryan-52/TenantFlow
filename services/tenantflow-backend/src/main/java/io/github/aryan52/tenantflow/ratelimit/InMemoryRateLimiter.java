package io.github.aryan52.tenantflow.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-instance fixed-window limiter. Used directly when {@code store=memory} and as the
 * fallback when Redis is unreachable, so limits keep being enforced (per instance) rather
 * than silently disappearing. Bounded: expired windows are purged, and if an attacker
 * floods it with unique keys the oldest state is dropped instead of exhausting memory.
 */
public class InMemoryRateLimiter implements RateLimiter {

	static final int MAX_KEYS = 100_000;

	private record Window(long startedAtMillis, long lengthMillis, long count) {
		boolean expired(long now) {
			return now >= startedAtMillis + lengthMillis;
		}
	}

	private final Map<String, Window> windows = new ConcurrentHashMap<>();
	private final Clock clock;

	public InMemoryRateLimiter() {
		this(Clock.systemUTC());
	}

	InMemoryRateLimiter(Clock clock) {
		this.clock = clock;
	}

	@Override
	public Decision tryConsume(String key, int limit, Duration window) {
		long now = clock.millis();
		if (windows.size() >= MAX_KEYS) {
			windows.values().removeIf(w -> w.expired(now));
			if (windows.size() >= MAX_KEYS) {
				windows.clear();
			}
		}
		Window updated = windows.compute(key, (k, w) -> w == null || w.expired(now)
				? new Window(now, window.toMillis(), 1)
				: new Window(w.startedAtMillis(), w.lengthMillis(), w.count() + 1));
		long retryAfter = Math.max(1, (updated.startedAtMillis() + updated.lengthMillis() - now + 999) / 1000);
		return new Decision(updated.count() <= limit, updated.count(), retryAfter);
	}

	int size() {
		return windows.size();
	}
}
