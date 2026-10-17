package io.github.aryan52.tenantflow.automation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AutomationRunRepository extends JpaRepository<AutomationRun, UUID> {

	boolean existsByTenantIdAndDedupeKey(UUID tenantId, String dedupeKey);

	Page<AutomationRun> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

	/** Per rule: [ruleType, runs, failures, lastRunAt]. */
	@Query("""
			select r.ruleType, count(r), sum(case when r.outcome = io.github.aryan52.tenantflow.automation.AutomationRun.Outcome.FAILED then 1 else 0 end), max(r.createdAt)
			from AutomationRun r where r.tenantId = :tenantId group by r.ruleType""")
	List<Object[]> summary(@Param("tenantId") UUID tenantId);

	void deleteAllByTenantId(UUID tenantId);
}
