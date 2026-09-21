package io.github.aryan52.tenantflow.repository;

import io.github.aryan52.tenantflow.entity.Project;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
	List<Project> findByTenant_Id(UUID tenantId);
	Optional<Project> findByIdAndTenant_Id(UUID id, UUID tenantId);
	long countByTenant_Id(UUID tenantId);
}
