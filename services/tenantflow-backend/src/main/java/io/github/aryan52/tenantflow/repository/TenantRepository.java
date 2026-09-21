package io.github.aryan52.tenantflow.repository;

import io.github.aryan52.tenantflow.entity.Tenant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

	Optional<Tenant> findBySlug(String slug);

	boolean existsBySlug(String slug);
}
