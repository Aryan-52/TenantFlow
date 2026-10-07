package io.github.aryan52.tenantflow.repository;

import io.github.aryan52.tenantflow.entity.Task;
import io.github.aryan52.tenantflow.entity.TaskStatus;
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
public interface TaskRepository extends JpaRepository<Task, UUID> {
	@EntityGraph(attributePaths = {"assignee", "createdBy"})
	List<Task> findByProject_IdOrderByCreatedAtAsc(UUID projectId);
	Optional<Task> findByIdAndProject_Id(UUID id, UUID projectId);
	
	long countByProject_Tenant_Id(UUID tenantId);
	long countByProject_Tenant_IdAndStatus(UUID tenantId, TaskStatus status);

	/** Per-project task totals for every project in a tenant (projects without tasks are absent). */
	@Query("""
			select t.project.id as projectId,
				count(t) as total,
				sum(case when t.status = :done then 1 else 0 end) as completed
			from Task t
			where t.project.tenant.id = :tenantId
			group by t.project.id
			""")
	List<ProjectTaskCounts> countByProjectForTenant(@Param("tenantId") UUID tenantId, @Param("done") TaskStatus done);

	@Modifying
	@Query("delete from Task t where t.project.id = :projectId")
	void deleteAllByProjectId(@Param("projectId") UUID projectId);

	long countByProject_Id(UUID projectId);
	long countByProject_IdAndStatus(UUID projectId, TaskStatus status);

	interface ProjectTaskCounts {
		UUID getProjectId();
		long getTotal();
		long getCompleted();
	}
}
