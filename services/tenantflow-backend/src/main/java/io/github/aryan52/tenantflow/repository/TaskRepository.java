package io.github.aryan52.tenantflow.repository;

import io.github.aryan52.tenantflow.entity.Task;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
	List<Task> findByProject_Id(UUID projectId);
	Optional<Task> findByIdAndProject_Id(UUID id, UUID projectId);
	
	long countByProject_Tenant_Id(UUID tenantId);
	long countByProject_Tenant_IdAndStatus(UUID tenantId, TaskStatus status);
}
