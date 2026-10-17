package io.github.aryan52.tenantflow.task;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {

	Page<TaskComment> findByTenantIdAndTaskId(UUID tenantId, UUID taskId, Pageable pageable);

	Optional<TaskComment> findByIdAndTenantIdAndTaskId(UUID id, UUID tenantId, UUID taskId);

	long countByTenantIdAndTaskId(UUID tenantId, UUID taskId);

	@Modifying
	@Query("delete from TaskComment c where c.taskId = :taskId")
	void deleteAllByTaskId(@Param("taskId") UUID taskId);
}
