package io.github.aryan52.tenantflow.ratelimit;

import java.time.Duration;

/** Fixed-window request counter: at most {@code limit} hits per {@code key} per {@code window}. */
public interface RateLimiter {

	/**
	 * @param allowed        whether this hit is within the limit
	 * @param count          hits recorded in the current window, including this one
	 * @param retryAfterSecs seconds until the window resets (only meaningful when denied)
	 */
	record Decision(boolean allowed, long count, long retryAfterSecs) {
	}

	Decision tryConsume(String key, int limit, Duration window);
}
