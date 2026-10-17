package io.github.aryan52.tenantflow.cache;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Evicts a tenant's cached summaries once the current transaction commits. Evicting after
 * commit (not before) prevents a concurrent request from re-populating the cache with the
 * pre-commit state; evicting nothing on rollback keeps the still-correct entry.
 */
@Component
public class TenantCacheInvalidator {

	private static final Logger log = LoggerFactory.getLogger(TenantCacheInvalidator.class);

	private final CacheManager cacheManager;

	public TenantCacheInvalidator(CacheManager cacheManager) {
		this.cacheManager = cacheManager;
	}

	public void invalidate(UUID tenantId) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					evict(tenantId);
				}
			});
		} else {
			evict(tenantId);
		}
	}

	private void evict(UUID tenantId) {
		Cache cache = cacheManager.getCache(CacheConfig.TENANT_STATS);
		if (cache != null) {
			// evictIfPresent is immediate (plain evict may be deferred). Programmatic calls do not
			// go through the CacheErrorHandler, so failures are handled here.
			try {
				cache.evictIfPresent(tenantId.toString());
			} catch (RuntimeException ex) {
				// The entry still expires via its TTL; the database stays authoritative.
				log.warn("Could not evict cached stats for tenant {} ({})", tenantId, ex.getClass().getSimpleName());
			}
		}
	}
}
