package io.github.aryan52.tenantflow.planning;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, UUID> {

	List<TaskDependency> findByTenantId(UUID tenantId);

	List<TaskDependency> findByBlockedTaskId(UUID blockedTaskId);

	List<TaskDependency> findByBlockingTaskId(UUID blockingTaskId);

	List<TaskDependency> findByBlockedTaskIdIn(Collection<UUID> blockedTaskIds);

	Optional<TaskDependency> findByIdAndTenantId(UUID id, UUID tenantId);

	boolean existsByBlockingTaskIdAndBlockedTaskId(UUID blockingTaskId, UUID blockedTaskId);

	/** Unfinished tasks blocking each of the given tasks. Rows: [blockedTaskId, count]. */
	@Query("""
			select d.blockedTaskId, count(d) from TaskDependency d, Task t
			where t.id = d.blockingTaskId and t.status <> io.github.aryan52.tenantflow.entity.TaskStatus.DONE
			and d.blockedTaskId in :ids group by d.blockedTaskId""")
	List<Object[]> countOpenBlockers(@Param("ids") Collection<UUID> ids);

	@Modifying
	@Query("delete from TaskDependency d where d.blockingTaskId = :taskId or d.blockedTaskId = :taskId")
	void deleteAllForTask(@Param("taskId") UUID taskId);

	@Modifying
	@Query("""
			delete from TaskDependency d where d.blockingTaskId in (select t.id from Task t where t.project.id = :projectId)
			or d.blockedTaskId in (select t.id from Task t where t.project.id = :projectId)""")
	void deleteAllForProject(@Param("projectId") UUID projectId);
}
