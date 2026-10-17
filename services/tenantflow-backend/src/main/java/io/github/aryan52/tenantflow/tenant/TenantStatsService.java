package io.github.aryan52.tenantflow.tenant;

import io.github.aryan52.tenantflow.cache.CacheConfig;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import io.github.aryan52.tenantflow.repository.TaskRepository;
import io.github.aryan52.tenantflow.task.TaskFocus;
import io.github.aryan52.tenantflow.tenant.dto.TenantStats;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-wide dashboard counts. Cached per tenant: the key is the tenant id, callers must
 * have already authorised access to that tenant, and {@code TenantCacheInvalidator} evicts
 * the entry after any project/task/membership change in the tenant.
 *
 * <p>Note: "overdue" and "due soon" depend on today's date, so a cached entry can lag by at most the TTL
 * around midnight - acceptable for a summary card.
 */
@Service
@RequiredArgsConstructor
public class TenantStatsService {

	private final ProjectRepository projectRepository;
	private final TaskRepository taskRepository;
	private final MembershipRepository membershipRepository;

	@Cacheable(cacheNames = CacheConfig.TENANT_STATS, key = "#tenantId.toString()")
	@Transactional(readOnly = true)
	public TenantStats statsFor(UUID tenantId) {
		LocalDate today = LocalDate.now();
		return new TenantStats(
				projectRepository.countByTenant_Id(tenantId),
				taskRepository.countByProject_Tenant_Id(tenantId),
				taskRepository.countByProject_Tenant_IdAndStatus(tenantId, TaskStatus.TODO),
				taskRepository.countByProject_Tenant_IdAndStatus(tenantId, TaskStatus.IN_PROGRESS),
				taskRepository.countByProject_Tenant_IdAndStatus(tenantId, TaskStatus.DONE),
				taskRepository.countByProject_Tenant_IdAndStatusNotAndDueDateBefore(tenantId, TaskStatus.DONE, today),
				taskRepository.countByProject_Tenant_IdAndStatusNotAndDueDateBetween(tenantId, TaskStatus.DONE, today, today.plusDays(TaskFocus.DUE_SOON_DAYS)),
				membershipRepository.countByTenant_Id(tenantId)
		);
	}
}
