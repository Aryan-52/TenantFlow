package io.github.aryan52.tenantflow.repository;

import io.github.aryan52.tenantflow.entity.Task;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
	long countByProject_Tenant_IdAndStatusNotAndDueDateBefore(UUID tenantId, TaskStatus status, LocalDate date);
	long countByProject_Tenant_IdAndStatusNotAndDueDateBetween(UUID tenantId, TaskStatus status, LocalDate from, LocalDate to);
	long countByProject_Tenant_IdAndAssignee_IdAndStatusNot(UUID tenantId, UUID assigneeId, TaskStatus status);

	// Tenant-wide task lists (dashboard and cross-project task views). Every query is scoped by tenant.
	@EntityGraph(attributePaths = {"project", "assignee", "createdBy"})
	Page<Task> findByProject_Tenant_IdAndStatusNot(UUID tenantId, TaskStatus status, Pageable pageable);

	@EntityGraph(attributePaths = {"project", "assignee", "createdBy"})
	Page<Task> findByProject_Tenant_IdAndAssignee_IdAndStatusNot(UUID tenantId, UUID assigneeId, TaskStatus status, Pageable pageable);

	@EntityGraph(attributePaths = {"project", "assignee", "createdBy"})
	Page<Task> findByProject_Tenant_IdAndStatusNotAndDueDateBefore(UUID tenantId, TaskStatus status, LocalDate date, Pageable pageable);

	@EntityGraph(attributePaths = {"project", "assignee", "createdBy"})
	Page<Task> findByProject_Tenant_IdAndStatusNotAndDueDateBetween(UUID tenantId, TaskStatus status, LocalDate from, LocalDate to, Pageable pageable);

	@EntityGraph(attributePaths = {"project", "assignee", "createdBy"})
	List<Task> findByProject_Tenant_IdAndStatusNotAndDueDateLessThanEqual(UUID tenantId, TaskStatus status, LocalDate date, Pageable pageable);

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

	/** Open (not done) tasks per assignee in a tenant, with how many are overdue. */
	@Query("""
			select t.assignee.id as userId,
				count(t) as open,
				sum(case when t.dueDate < :today then 1 else 0 end) as overdue
			from Task t
			where t.project.tenant.id = :tenantId and t.status <> :done and t.assignee is not null
			group by t.assignee.id
			""")
	List<AssigneeWorkload> workloadByAssignee(@Param("tenantId") UUID tenantId, @Param("done") TaskStatus done,
			@Param("today") LocalDate today);

	interface AssigneeWorkload {
		UUID getUserId();
		long getOpen();
		long getOverdue();
	}

	interface ProjectTaskCounts {
		UUID getProjectId();
		long getTotal();
		long getCompleted();
	}

	// ---- TenantFlow 2.0 queries (all tenant-scoped through project.tenant) -----------------

	@EntityGraph(attributePaths = {"project", "assignee", "createdBy"})
	Optional<Task> findByIdAndProject_Tenant_Id(UUID id, UUID tenantId);

	@EntityGraph(attributePaths = {"project", "assignee", "createdBy"})
	List<Task> findByIdInAndProject_Tenant_Id(java.util.Collection<UUID> ids, UUID tenantId);

	/** A user's open tasks in a workspace (My Work), with everything a row shows. */
	@EntityGraph(attributePaths = {"project", "assignee", "createdBy"})
	List<Task> findTop500ByProject_Tenant_IdAndAssignee_IdAndStatusNotOrderByDueDateAscCreatedAtAsc(
			UUID tenantId, UUID assigneeId, TaskStatus status);

	/** A user's tasks completed since a point in time (My Work "completed recently"). */
	@EntityGraph(attributePaths = {"project", "assignee", "createdBy"})
	List<Task> findTop50ByProject_Tenant_IdAndAssignee_IdAndStatusAndCompletedAtGreaterThanEqualOrderByCompletedAtDesc(
			UUID tenantId, UUID assigneeId, TaskStatus status, java.time.Instant since);

	/** Tasks scheduled in a date window (due or starting in it, or spanning it). */
	@EntityGraph(attributePaths = {"project", "assignee", "createdBy"})
	@Query("""
			select t from Task t where t.project.tenant.id = :tenantId
			and ((t.dueDate between :from and :to) or (t.startDate between :from and :to)
				or (t.startDate < :from and t.dueDate > :to))
			order by t.dueDate asc, t.createdAt asc""")
	List<Task> findScheduled(@Param("tenantId") UUID tenantId, @Param("from") LocalDate from, @Param("to") LocalDate to,
			Pageable pageable);

	/** Open, assigned tasks due on or before a date, across all workspaces (reminders, automations). */
	@EntityGraph(attributePaths = {"project", "project.tenant", "project.owner", "assignee"})
	@Query("""
			select t from Task t where t.status <> io.github.aryan52.tenantflow.entity.TaskStatus.DONE
			and t.dueDate is not null and t.dueDate <= :until""")
	List<Task> findOpenDueOnOrBefore(@Param("until") LocalDate until, Pageable pageable);

	/** Title lookup inside one workspace (dependency picker). */
	@EntityGraph(attributePaths = {"project"})
	@Query("""
			select t from Task t where t.project.tenant.id = :tenantId
			and lower(t.title) like lower(concat('%', :q, '%')) escape '\\'
			order by t.updatedAt desc""")
	List<Task> lookup(@Param("tenantId") UUID tenantId, @Param("q") String q, Pageable pageable);

	boolean existsBySeriesIdAndDueDate(UUID seriesId, LocalDate dueDate);

	/** All tasks of a workspace with their project and assignee (portfolio, planning, reports). */
	@EntityGraph(attributePaths = {"project", "assignee"})
	List<Task> findByProject_Tenant_Id(UUID tenantId);

	@EntityGraph(attributePaths = {"project", "assignee"})
	List<Task> findByMilestoneIdAndStatusNot(UUID milestoneId, TaskStatus status);
}
