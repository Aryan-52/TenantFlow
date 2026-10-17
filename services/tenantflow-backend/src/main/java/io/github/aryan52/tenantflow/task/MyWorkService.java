package io.github.aryan52.tenantflow.task;

import io.github.aryan52.tenantflow.entity.Task;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import io.github.aryan52.tenantflow.error.ResourceNotFoundException;
import io.github.aryan52.tenantflow.preferences.PreferenceService;
import io.github.aryan52.tenantflow.repository.TaskRepository;
import io.github.aryan52.tenantflow.task.dto.TaskResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The current user's work in one workspace: every open task assigned to them (with blocked
 * counts), what they pinned for today, and what they finished recently. "Today" is computed
 * in the user's own time zone. Nothing here creates deadlines or commitments.
 */
@Service
@RequiredArgsConstructor
public class MyWorkService {

	public static final int COMPLETED_DAYS = 14;

	public record MyWork(LocalDate today, List<TaskResponse> open, List<UUID> focusTaskIds, List<TaskResponse> completed,
			int completedDays) {
	}

	private final TaskRepository taskRepository;
	private final TaskService taskService;
	private final TaskFocusRepository focusRepository;
	private final PreferenceService preferenceService;

	@Transactional(readOnly = true)
	public MyWork get(UUID tenantId, UUID userId) {
		LocalDate today = preferenceService.today(userId);
		List<Task> open = taskRepository.findTop500ByProject_Tenant_IdAndAssignee_IdAndStatusNotOrderByDueDateAscCreatedAtAsc(
				tenantId, userId, TaskStatus.DONE);
		List<Task> completed = taskRepository.findTop50ByProject_Tenant_IdAndAssignee_IdAndStatusAndCompletedAtGreaterThanEqualOrderByCompletedAtDesc(
				tenantId, userId, TaskStatus.DONE, Instant.now().minus(COMPLETED_DAYS, ChronoUnit.DAYS));
		// A pin only counts on the day it was made, and only for open tasks of this workspace.
		List<UUID> openIds = open.stream().map(Task::getId).toList();
		List<UUID> focus = focusRepository.findByUserId(userId).stream()
				.filter(f -> f.getFocusDate().equals(today) && openIds.contains(f.getTaskId()))
				.map(TaskFocusEntry::getTaskId).toList();
		return new MyWork(today, taskService.withBlockers(open), focus,
				completed.stream().map(TaskResponse::from).toList(), COMPLETED_DAYS);
	}

	/** Pin a task to today. Idempotent. */
	@Transactional
	public void focus(UUID tenantId, UUID userId, UUID taskId) {
		requireTaskInWorkspace(tenantId, taskId);
		TaskFocusEntry entry = focusRepository.findById(new TaskFocusEntry.Key(userId, taskId))
				.orElseGet(() -> TaskFocusEntry.builder().userId(userId).taskId(taskId).build());
		entry.setFocusDate(preferenceService.today(userId));
		focusRepository.save(entry);
	}

	@Transactional
	public void unfocus(UUID tenantId, UUID userId, UUID taskId) {
		requireTaskInWorkspace(tenantId, taskId);
		focusRepository.deleteById(new TaskFocusEntry.Key(userId, taskId));
	}

	private void requireTaskInWorkspace(UUID tenantId, UUID taskId) {
		taskRepository.findByIdAndProject_Tenant_Id(taskId, tenantId).orElseThrow(() -> new ResourceNotFoundException("Task"));
	}
}
