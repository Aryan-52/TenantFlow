package io.github.aryan52.tenantflow.cache;

import io.github.aryan52.tenantflow.tenant.dto.TenantStats;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Spring cache abstraction backed by Redis (PostgreSQL remains the source of truth).
 *
 * <p>Only small, frequently read, tenant-scoped summaries are cached. Every cache key
 * starts with the tenant id, entries are evicted after the commit of any change in that
 * tenant ({@link TenantCacheInvalidator}), and a short TTL bounds staleness should an
 * eviction ever be missed. Cache failures (e.g. Redis down) are logged and treated as
 * misses, so requests fall back to the database instead of failing.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

	/** Bump the "v2" key prefix below whenever a cached value's JSON shape changes. */
	public static final String TENANT_STATS = "tenantStats";

	private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

	private final MeterRegistry meterRegistry;

	public CacheConfig(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	@Bean
	public RedisCacheManagerBuilderCustomizer tenantflowRedisCaches() {
		return builder -> builder
				.cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()
						.prefixCacheNameWith("tenantflow:cache:v2:")
						.serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
						.entryTtl(Duration.ofMinutes(5))
						.disableCachingNullValues())
				.withCacheConfiguration(TENANT_STATS, RedisCacheConfiguration.defaultCacheConfig()
						.prefixCacheNameWith("tenantflow:cache:v2:")
						.serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
						.serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(TenantStats.class)))
						.entryTtl(Duration.ofMinutes(5))
						.disableCachingNullValues())
				.enableStatistics();
	}

	@Override
	public CacheErrorHandler errorHandler() {
		return new CacheErrorHandler() {
			@Override
			public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
				failed("get", ex, cache);
			}

			@Override
			public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
				failed("put", ex, cache);
			}

			@Override
			public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
				failed("evict", ex, cache);
			}

			@Override
			public void handleCacheClearError(RuntimeException ex, Cache cache) {
				failed("clear", ex, cache);
			}
		};
	}

	private void failed(String operation, RuntimeException ex, Cache cache) {
		meterRegistry.counter("tenantflow.cache.errors", "cache", cache.getName(), "operation", operation).increment();
		log.warn("Cache {} on '{}' failed ({}); serving from the database", operation, cache.getName(),
				ex.getClass().getSimpleName());
	}
}
