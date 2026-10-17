package io.github.aryan52.tenantflow.activity;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, UUID> {

	Page<ActivityEvent> findByTenantId(UUID tenantId, Pageable pageable);

	Page<ActivityEvent> findByTenantIdAndProjectId(UUID tenantId, UUID projectId, Pageable pageable);

	Page<ActivityEvent> findByTenantIdAndTaskId(UUID tenantId, UUID taskId, Pageable pageable);

	@Modifying
	@Query("delete from ActivityEvent a where a.tenantId = :tenantId")
	void deleteAllByTenantId(@Param("tenantId") UUID tenantId);

	/** A deleted project's history stays, detached from the project (mirrors ON DELETE SET NULL). */
	@Modifying
	@Query("update ActivityEvent a set a.projectId = null where a.projectId = :projectId")
	void detachProject(@Param("projectId") UUID projectId);
}
