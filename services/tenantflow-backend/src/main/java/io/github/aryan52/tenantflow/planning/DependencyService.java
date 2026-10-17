package io.github.aryan52.tenantflow.planning;

import io.github.aryan52.tenantflow.activity.ActivityService;
import io.github.aryan52.tenantflow.entity.Task;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import io.github.aryan52.tenantflow.error.ConflictException;
import io.github.aryan52.tenantflow.error.ResourceNotFoundException;
import io.github.aryan52.tenantflow.repository.TaskRepository;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Blocked by" relationships between tasks of one workspace. Both tasks are always looked up
 * inside the caller's workspace, self-links and cycles are rejected, and a blocked task cannot
 * be marked done while any of its blockers is unfinished (enforced by TaskService through
 * {@link #requireUnblocked}).
 */
@Service
@RequiredArgsConstructor
public class DependencyService {

	public record TaskRef(UUID dependencyId, UUID id, UUID projectId, String projectName, String title,
			TaskStatus status, LocalDate dueDate, String assigneeName) {
	}

	public record Dependencies(List<TaskRef> blockedBy, List<TaskRef> blocks, int openBlockers) {
	}

	public record TaskLookup(UUID id, UUID projectId, String projectName, String title, TaskStatus status) {
	}

	private final TaskDependencyRepository dependencyRepository;
	private final TaskRepository taskRepository;
	private final ActivityService activityService;

	@Transactional(readOnly = true)
	public Dependencies get(UUID tenantId, UUID taskId) {
		findTask(tenantId, taskId);
		List<TaskDependency> blockedBy = dependencyRepository.findByBlockedTaskId(taskId);
		List<TaskDependency> blocks = dependencyRepository.findByBlockingTaskId(taskId);
		Set<UUID> ids = new HashSet<>();
		blockedBy.forEach(d -> ids.add(d.getBlockingTaskId()));
		blocks.forEach(d -> ids.add(d.getBlockedTaskId()));
		Map<UUID, Task> tasks = taskRepository.findByIdInAndProject_Tenant_Id(ids, tenantId).stream()
				.collect(Collectors.toMap(Task::getId, Function.identity()));
		List<TaskRef> blockedByRefs = blockedBy.stream().filter(d -> tasks.containsKey(d.getBlockingTaskId()))
				.map(d -> ref(d.getId(), tasks.get(d.getBlockingTaskId()))).toList();
		List<TaskRef> blocksRefs = blocks.stream().filter(d -> tasks.containsKey(d.getBlockedTaskId()))
				.map(d -> ref(d.getId(), tasks.get(d.getBlockedTaskId()))).toList();
		int open = (int) blockedByRefs.stream().filter(r -> r.status() != TaskStatus.DONE).count();
		return new Dependencies(blockedByRefs, blocksRefs, open);
	}

	/** Records that {@code blockingTaskId} must be finished before {@code blockedTaskId}. */
	@Transactional
	public Dependencies add(UUID tenantId, UUID blockedTaskId, UUID blockingTaskId, UUID userId) {
		if (blockingTaskId == null) {
			throw new IllegalArgumentException("Choose the task that blocks this one");
		}
		if (blockedTaskId.equals(blockingTaskId)) {
			throw new IllegalArgumentException("A task cannot depend on itself");
		}
		Task blocked = findTask(tenantId, blockedTaskId);
		// Looked up in the same workspace: a task id from another workspace is simply "not found".
		Task blocking = findTask(tenantId, blockingTaskId);
		if (dependencyRepository.existsByBlockingTaskIdAndBlockedTaskId(blockingTaskId, blockedTaskId)) {
			return get(tenantId, blockedTaskId); // idempotent: a retried request changes nothing
		}
		if (reaches(tenantId, blockedTaskId, blockingTaskId)) {
			throw new ConflictException("\"" + blocking.getTitle() + "\" already waits on \"" + blocked.getTitle()
					+ "\" (directly or through other tasks). Adding this would create a circular dependency.");
		}
		dependencyRepository.saveAndFlush(TaskDependency.builder()
				.tenantId(tenantId)
				.blockingTaskId(blockingTaskId)
				.blockedTaskId(blockedTaskId)
				.createdBy(userId)
				.build());
		activityService.record(tenantId, blocked.getProject().getId(), blocked.getId(), "TASK_DEPENDENCY_ADDED",
				"marked \"" + blocked.getTitle() + "\" as blocked by \"" + blocking.getTitle() + "\"", List.of());
		return get(tenantId, blockedTaskId);
	}

	@Transactional
	public Dependencies remove(UUID tenantId, UUID taskId, UUID dependencyId) {
		TaskDependency d = dependencyRepository.findByIdAndTenantId(dependencyId, tenantId)
				.filter(x -> x.getBlockedTaskId().equals(taskId) || x.getBlockingTaskId().equals(taskId))
				.orElseThrow(() -> new ResourceNotFoundException("Dependency"));
		Task blocked = findTask(tenantId, d.getBlockedTaskId());
		Task blocking = taskRepository.findByIdAndProject_Tenant_Id(d.getBlockingTaskId(), tenantId).orElse(null);
		dependencyRepository.delete(d);
		activityService.record(tenantId, blocked.getProject().getId(), blocked.getId(), "TASK_DEPENDENCY_REMOVED",
				"removed the dependency of \"" + blocked.getTitle() + "\" on "
						+ (blocking == null ? "a task" : "\"" + blocking.getTitle() + "\""), List.of());
		return get(tenantId, taskId);
	}

	/** Throws when the task still waits on unfinished tasks; the message names them. */
	@Transactional(readOnly = true)
	public void requireUnblocked(UUID tenantId, Task task) {
		List<TaskDependency> deps = dependencyRepository.findByBlockedTaskId(task.getId());
		if (deps.isEmpty()) {
			return;
		}
		List<Task> open = taskRepository.findByIdInAndProject_Tenant_Id(
				deps.stream().map(TaskDependency::getBlockingTaskId).toList(), tenantId).stream()
				.filter(t -> t.getStatus() != TaskStatus.DONE).toList();
		if (!open.isEmpty()) {
			String names = open.stream().limit(3).map(t -> "\"" + t.getTitle() + "\"").collect(Collectors.joining(", "));
			throw new ConflictException("\"" + task.getTitle() + "\" is blocked by " + open.size() + " unfinished task"
					+ (open.size() == 1 ? "" : "s") + ": " + names + (open.size() > 3 ? " and more" : "")
					+ ". Finish " + (open.size() == 1 ? "it" : "them") + " first, or remove the dependency.");
		}
	}

	/** Unfinished blockers per task, in one query. Tasks without blockers are absent. */
	@Transactional(readOnly = true)
	public Map<UUID, Integer> openBlockerCounts(Collection<UUID> taskIds) {
		Map<UUID, Integer> counts = new HashMap<>();
		if (taskIds.isEmpty()) {
			return counts;
		}
		for (Object[] row : dependencyRepository.countOpenBlockers(taskIds)) {
			counts.put((UUID) row[0], ((Number) row[1]).intValue());
		}
		return counts;
	}

	/** Tasks waiting on the given task (to tell their assignees when it is done). */
	@Transactional(readOnly = true)
	public List<Task> dependents(UUID tenantId, UUID taskId) {
		List<UUID> ids = dependencyRepository.findByBlockingTaskId(taskId).stream().map(TaskDependency::getBlockedTaskId).toList();
		return ids.isEmpty() ? List.of() : taskRepository.findByIdInAndProject_Tenant_Id(ids, tenantId);
	}

	/** Title search inside one workspace for the dependency picker (LIKE wildcards in the text are literal). */
	@Transactional(readOnly = true)
	public List<TaskLookup> lookup(UUID tenantId, String q) {
		String term = q == null ? "" : q.trim();
		if (term.length() > 100) {
			throw new IllegalArgumentException("Search text is too long");
		}
		String escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
		return taskRepository.lookup(tenantId, escaped, org.springframework.data.domain.PageRequest.of(0, 20)).stream()
				.map(t -> new TaskLookup(t.getId(), t.getProject().getId(), t.getProject().getName(), t.getTitle(), t.getStatus()))
				.toList();
	}

	/** Is {@code target} reachable from {@code start} following "blocks" edges? */
	private boolean reaches(UUID tenantId, UUID start, UUID target) {
		Map<UUID, List<UUID>> blocks = new HashMap<>();
		for (TaskDependency d : dependencyRepository.findByTenantId(tenantId)) {
			blocks.computeIfAbsent(d.getBlockingTaskId(), k -> new java.util.ArrayList<>()).add(d.getBlockedTaskId());
		}
		Deque<UUID> queue = new ArrayDeque<>(List.of(start));
		Set<UUID> seen = new HashSet<>();
		while (!queue.isEmpty()) {
			UUID current = queue.poll();
			if (current.equals(target)) {
				return true;
			}
			if (seen.add(current)) {
				queue.addAll(blocks.getOrDefault(current, List.of()));
			}
		}
		return false;
	}

	private Task findTask(UUID tenantId, UUID taskId) {
		return taskRepository.findByIdAndProject_Tenant_Id(taskId, tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Task"));
	}

	private static TaskRef ref(UUID dependencyId, Task t) {
		return new TaskRef(dependencyId, t.getId(), t.getProject().getId(), t.getProject().getName(), t.getTitle(),
				t.getStatus(), t.getDueDate(), t.getAssignee() == null ? null : t.getAssignee().getName());
	}
}
