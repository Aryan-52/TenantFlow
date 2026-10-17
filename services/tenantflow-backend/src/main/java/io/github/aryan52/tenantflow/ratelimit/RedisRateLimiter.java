package io.github.aryan52.tenantflow.ratelimit;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Fixed-window limiter shared by every application instance. INCR and PEXPIRE run in one
 * Lua script, so the window is created atomically and can never be left without a TTL.
 */
public class RedisRateLimiter implements RateLimiter {

	private static final String KEY_PREFIX = "tenantflow:ratelimit:";

	@SuppressWarnings("rawtypes")
	private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>("""
			local count = redis.call('INCR', KEYS[1])
			if count == 1 then
			  redis.call('PEXPIRE', KEYS[1], ARGV[1])
			end
			local ttl = redis.call('PTTL', KEYS[1])
			if ttl < 0 then
			  redis.call('PEXPIRE', KEYS[1], ARGV[1])
			  ttl = tonumber(ARGV[1])
			end
			return {count, ttl}
			""", List.class);

	private final StringRedisTemplate redis;

	public RedisRateLimiter(StringRedisTemplate redis) {
		this.redis = redis;
	}

	@Override
	public Decision tryConsume(String key, int limit, Duration window) {
		@SuppressWarnings("unchecked")
		List<Long> result = redis.execute(SCRIPT, List.of(KEY_PREFIX + key), String.valueOf(window.toMillis()));
		if (result == null || result.size() < 2) {
			throw new IllegalStateException("Unexpected rate-limit script result");
		}
		long count = result.get(0);
		long ttlMillis = result.get(1);
		return new Decision(count <= limit, count, Math.max(1, (ttlMillis + 999) / 1000));
	}
}
