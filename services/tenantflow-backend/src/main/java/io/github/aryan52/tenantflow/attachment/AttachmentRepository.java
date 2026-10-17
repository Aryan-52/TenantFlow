package io.github.aryan52.tenantflow.attachment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every lookup is scoped by tenant and project, never by attachment id alone. */
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

	Page<Attachment> findByTenantIdAndProjectId(UUID tenantId, UUID projectId, Pageable pageable);

	Page<Attachment> findByTenantIdAndProjectIdAndTaskId(UUID tenantId, UUID projectId, UUID taskId, Pageable pageable);

	Optional<Attachment> findByIdAndTenantIdAndProjectId(UUID id, UUID tenantId, UUID projectId);

	@Query("select a.storageKey from Attachment a where a.taskId = :taskId")
	List<String> storageKeysForTask(@Param("taskId") UUID taskId);

	@Query("select a.storageKey from Attachment a where a.projectId = :projectId")
	List<String> storageKeysForProject(@Param("projectId") UUID projectId);

	@Query("select a.storageKey from Attachment a where a.tenantId = :tenantId")
	List<String> storageKeysForTenant(@Param("tenantId") UUID tenantId);

	/** A moved task takes its attachments to the new project (storage keys are opaque and stay). */
	@Modifying
	@Query("update Attachment a set a.projectId = :projectId where a.taskId = :taskId")
	void moveTaskAttachments(@Param("taskId") UUID taskId, @Param("projectId") UUID projectId);

	@Modifying
	@Query("delete from Attachment a where a.taskId = :taskId")
	void deleteAllByTaskId(@Param("taskId") UUID taskId);

	@Modifying
	@Query("delete from Attachment a where a.projectId = :projectId")
	void deleteAllByProjectId(@Param("projectId") UUID projectId);

	@Modifying
	@Query("delete from Attachment a where a.tenantId = :tenantId")
	void deleteAllByTenantId(@Param("tenantId") UUID tenantId);
}
