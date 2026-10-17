package io.github.aryan52.tenantflow.repository;

import io.github.aryan52.tenantflow.entity.Membership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import io.github.aryan52.tenantflow.entity.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

	Optional<Membership> findByUser_IdAndTenant_Id(UUID userId, UUID tenantId);

	boolean existsByUser_IdAndTenant_Id(UUID userId, UUID tenantId);

	List<Membership> findByUser_Id(UUID userId);

	List<Membership> findByTenant_Id(UUID tenantId);

	long countByTenant_IdAndRole(UUID tenantId, Role role);

	/** SELECT ... FOR UPDATE: serialises concurrent changes to a workspace's owners. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from Membership m where m.tenant.id = :tenantId and m.role = :role")
	List<Membership> lockByTenantAndRole(@Param("tenantId") UUID tenantId, @Param("role") Role role);

	long countByTenant_Id(UUID tenantId);
}
