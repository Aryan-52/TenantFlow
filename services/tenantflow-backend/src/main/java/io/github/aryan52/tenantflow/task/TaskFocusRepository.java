package io.github.aryan52.tenantflow.task;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskFocusRepository extends JpaRepository<TaskFocusEntry, TaskFocusEntry.Key> {

	List<TaskFocusEntry> findByUserId(UUID userId);

	@Modifying
	@Query("delete from TaskFocusEntry f where f.taskId = :taskId")
	void deleteAllByTaskId(@Param("taskId") UUID taskId);

	@Modifying
	@Query("delete from TaskFocusEntry f where f.taskId in (select t.id from Task t where t.project.id = :projectId)")
	void deleteAllForProject(@Param("projectId") UUID projectId);
}
