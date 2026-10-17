package io.github.aryan52.tenantflow.repository;

import io.github.aryan52.tenantflow.entity.Project;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
	@EntityGraph(attributePaths = {"createdBy", "owner"})
	List<Project> findByTenant_Id(UUID tenantId);
	Optional<Project> findByIdAndTenant_Id(UUID id, UUID tenantId);
	long countByTenant_Id(UUID tenantId);
	@EntityGraph(attributePaths = {"owner"})
	List<Project> findByTenant_IdOrderByUpdatedAtDesc(UUID tenantId, org.springframework.data.domain.Pageable pageable);

	/** Unassigns a user from every project they own in one workspace (e.g. when they leave it). */
	@Modifying
	@Query("update Project p set p.owner = null where p.tenant.id = :tenantId and p.owner.id = :userId")
	int clearOwnerInTenant(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);
}
