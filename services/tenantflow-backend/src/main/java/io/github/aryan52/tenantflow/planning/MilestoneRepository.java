package io.github.aryan52.tenantflow.planning;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {

	List<Milestone> findByProjectIdOrderByTargetDateAscNameAsc(UUID projectId);

	Optional<Milestone> findByIdAndProjectIdAndTenantId(UUID id, UUID projectId, UUID tenantId);

	List<Milestone> findByTenantIdAndTargetDateBetweenOrderByTargetDateAsc(UUID tenantId, LocalDate from, LocalDate to);

	List<Milestone> findByTenantIdOrderByTargetDateAsc(UUID tenantId);

	List<Milestone> findByIdIn(Collection<UUID> ids);

	/** Per milestone: total and done tasks linked to it. Rows: [milestoneId, total, done]. */
	@Query("""
			select t.milestoneId, count(t), sum(case when t.status = io.github.aryan52.tenantflow.entity.TaskStatus.DONE then 1 else 0 end)
			from Task t where t.milestoneId in :ids group by t.milestoneId""")
	List<Object[]> taskCounts(@Param("ids") Collection<UUID> ids);

	@Modifying
	@Query("delete from Milestone m where m.projectId = :projectId")
	void deleteAllByProjectId(@Param("projectId") UUID projectId);

	@Modifying
	@Query("update Task t set t.milestoneId = null where t.milestoneId = :milestoneId")
	void detachTasks(@Param("milestoneId") UUID milestoneId);
}
