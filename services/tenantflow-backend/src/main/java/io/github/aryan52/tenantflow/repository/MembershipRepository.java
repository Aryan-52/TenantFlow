package io.github.aryan52.tenantflow.repository;

import io.github.aryan52.tenantflow.entity.Membership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

	Optional<Membership> findByUser_IdAndTenant_Id(UUID userId, UUID tenantId);

	boolean existsByUser_IdAndTenant_Id(UUID userId, UUID tenantId);

	List<Membership> findByUser_Id(UUID userId);

	List<Membership> findByTenant_Id(UUID tenantId);

	long countByTenant_IdAndRole(UUID tenantId, io.github.aryan52.tenantflow.entity.Role role);

	long countByTenant_Id(UUID tenantId);
}
