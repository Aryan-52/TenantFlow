package io.github.aryan52.tenantflow.task;

import io.github.aryan52.tenantflow.cache.TenantCacheInvalidator;
import io.github.aryan52.tenantflow.attachment.AttachmentService;
import io.github.aryan52.tenantflow.entity.Project;
import io.github.aryan52.tenantflow.attachment.AttachmentRepository;
import io.github.aryan52.tenantflow.idempotency.IdempotencyService;
import io.github.aryan52.tenantflow.entity.Task;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.error.ConflictException;
import io.github.aryan52.tenantflow.error.ResourceNotFoundException;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import io.github.aryan52.tenantflow.repository.TaskRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.task.dto.CreateTaskRequest;
import io.github.aryan52.tenantflow.task.dto.TaskResponse;
import io.github.aryan52.tenantflow.task.dto.UpdateTaskRequest;
import io.github.aryan52.tenantflow.task.dto.UpdateTaskStatusRequest;
import io.github.aryan52.tenantflow.common.PageResponse;
import io.github.aryan52.tenantflow.common.Pagination;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import io.github.aryan52.tenantflow.automation.AutomationService;
import io.github.aryan52.tenantflow.planning.DependencyService;
import io.github.aryan52.tenantflow.planning.MilestoneService;
import io.github.aryan52.tenantflow.planning.Recurrences;
import io.github.aryan52.tenantflow.planning.TaskDependencyRepository;
import io.github.aryan52.tenantflow.task.dto.TaskPlanning;
import java.util.Map;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskService {

	/** Soonest due first; tasks without a due date last; then oldest first for a stable order. */
	public static final Sort BY_DUE_DATE = Sort.by(Sort.Order.asc("dueDate").nullsLast(), Sort.Order.asc("createdAt"), Sort.Order.asc("id"));

	private final TaskRepository taskRepository;
	private final ProjectRepository projectRepository;
	private final UserRepository userRepository;
	private final MembershipRepository membershipRepository;
	private final TenantCacheInvalidator cacheInvalidator;
	private final TaskActivity taskActivity;
	private final IdempotencyService idempotencyService;
	private final AttachmentRepository attachmentRepository;
	private final AttachmentService attachmentService;
	private final TaskCommentRepository commentRepository;
	private final DependencyService dependencyService;
	private final TaskDependencyRepository dependencyRepository;
	private final MilestoneService milestoneService;
	private final AutomationService automationService;
	private final TaskFocusRepository focusRepository;

	@Transactional(readOnly = true)
	public List<TaskResponse> getTasks(UUID tenantId, UUID projectId) {
		findProject(tenantId, projectId);
		return withBlockers(taskRepository.findByProject_IdOrderByCreatedAtAsc(projectId));
	}

	/** Open tasks across all projects of the tenant, soonest due first (no due date last). */
	@Transactional(readOnly = true)
	public PageResponse<TaskResponse> getTenantTasks(UUID tenantId, UUID currentUserId, TaskFocus focus, Integer page, Integer size) {
		Pageable pageable = Pagination.of(page, size, BY_DUE_DATE);
		LocalDate today = LocalDate.now();
		Page<Task> tasks = switch (focus) {
			case OPEN -> taskRepository.findByProject_Tenant_IdAndStatusNot(tenantId, TaskStatus.DONE, pageable);
			case MINE -> taskRepository.findByProject_Tenant_IdAndAssignee_IdAndStatusNot(tenantId, currentUserId, TaskStatus.DONE, pageable);
			case OVERDUE -> taskRepository.findByProject_Tenant_IdAndStatusNotAndDueDateBefore(tenantId, TaskStatus.DONE, today, pageable);
			case DUE_SOON -> taskRepository.findByProject_Tenant_IdAndStatusNotAndDueDateBetween(
					tenantId, TaskStatus.DONE, today, today.plusDays(TaskFocus.DUE_SOON_DAYS), pageable);
		};
		Map<UUID, Integer> blockers = dependencyService.openBlockerCounts(tasks.getContent().stream().map(Task::getId).toList());
		return PageResponse.of(tasks, t -> TaskResponse.from(t, blockers.getOrDefault(t.getId(), 0)));
	}

	/** Responses with their unfinished-blocker counts, computed in one query. */
	public List<TaskResponse> withBlockers(List<Task> tasks) {
		Map<UUID, Integer> blockers = dependencyService.openBlockerCounts(tasks.stream().map(Task::getId).toList());
		return tasks.stream().map(t -> TaskResponse.from(t, blockers.getOrDefault(t.getId(), 0))).collect(Collectors.toList());
	}

	private TaskResponse withBlockers(Task task) {
		return withBlockers(List.of(task)).get(0);
	}

	@Transactional(readOnly = true)
	public TaskResponse getTask(UUID tenantId, UUID projectId, UUID taskId) {
		return withBlockers(findTask(tenantId, projectId, taskId));
	}

	@Transactional
	public TaskResponse createTask(UUID tenantId, UUID projectId, UUID currentUserId, CreateTaskRequest request) {
		return createTask(tenantId, projectId, currentUserId, request, null);
	}

	/** With an Idempotency-Key, a replayed create returns the task the first request created. */
	@Transactional
	public TaskResponse createTask(UUID tenantId, UUID projectId, UUID currentUserId, CreateTaskRequest request,
			String idempotencyKey) {
		Project project = findProject(tenantId, projectId);
		UUID taskId = idempotencyService.run(currentUserId, "task-create:" + projectId, idempotencyKey, request,
				() -> doCreateTask(tenantId, project, currentUserId, request));
		// The original task may have been moved since, but never out of this tenant.
		return taskRepository.findById(taskId)
				.filter(t -> t.getProject().getTenant().getId().equals(tenantId))
				.map(this::withBlockers)
				.orElseThrow(() -> new ResourceNotFoundException("Task"));
	}

	private UUID doCreateTask(UUID tenantId, Project project, UUID currentUserId, CreateTaskRequest request) {

		Task task = Task.builder()
				.project(project)
				.title(request.title().trim())
				.description(trimToNull(request.description()))
				.status(request.status())
				.priority(request.priority())
				.assignee(resolveAssignee(tenantId, request.assigneeId(), null))
				.createdBy(userRepository.getReferenceById(currentUserId))
				.dueDate(request.dueDate())
				.build();
		applyPlanning(tenantId, project.getId(), task, request.planning());
		if (task.getStatus() == TaskStatus.DONE && task.getRecurrenceFrequency() != null) {
			throw new IllegalArgumentException("A recurring task must start open");
		}

		Task saved = taskRepository.saveAndFlush(task);
		taskActivity.created(tenantId, saved);
		if (saved.getPriority() == io.github.aryan52.tenantflow.entity.TaskPriority.URGENT) {
			automationService.onUrgent(tenantId, saved);
		}
		cacheInvalidator.invalidate(tenantId);
		return saved.getId();
	}

	@Transactional
	public TaskResponse updateTask(UUID tenantId, UUID projectId, UUID taskId, UpdateTaskRequest request) {
		Task task = findTask(tenantId, projectId, taskId);
		if (request.version() != null && request.version() != task.getVersion()) {
			throw new ConflictException("This task was changed by someone else. Reload it and try again.");
		}
		TaskActivity.Snapshot before = TaskActivity.Snapshot.of(task);

		boolean moved = request.projectId() != null && !request.projectId().equals(projectId);
		if (moved) {
			// Target is looked up within the same tenant, so a task can never be moved
			// into (or reveal the existence of) another workspace's project.
			task.setProject(findProject(tenantId, request.projectId()));
			attachmentRepository.moveTaskAttachments(task.getId(), request.projectId());
			// Milestones belong to one project; the link does not travel with the task.
			task.setMilestoneId(null);
		}
		if (request.status() == TaskStatus.DONE && before.status() != TaskStatus.DONE) {
			dependencyService.requireUnblocked(tenantId, task);
		}

		task.setTitle(request.title().trim());
		task.setDescription(trimToNull(request.description()));
		task.setStatus(request.status());
		task.setPriority(request.priority());
		task.setAssignee(resolveAssignee(tenantId, request.assigneeId(), task.getAssignee()));
		if (task.getSeriesId() != null && request.dueDate() != null && !request.dueDate().equals(task.getDueDate())
				&& taskRepository.existsBySeriesIdAndDueDate(task.getSeriesId(), request.dueDate())) {
			throw new IllegalArgumentException("Another occurrence of this recurring task is already due on " + request.dueDate());
		}
		task.setDueDate(request.dueDate());
		if (request.planning() != null) {
			applyPlanning(tenantId, task.getProject().getId(), task, request.planning());
		} else {
			validateDates(task);
			if (task.getRecurrenceFrequency() != null && task.getDueDate() == null) {
				throw new IllegalArgumentException("A recurring task needs a due date. Remove the repeat rule to clear it.");
			}
		}

		Task saved = taskRepository.saveAndFlush(task);
		taskActivity.updated(tenantId, before, saved);
		afterChange(tenantId, before.status(), before.priority(), saved);
		cacheInvalidator.invalidate(tenantId);
		return withBlockers(saved);
	}

	@Transactional
	public TaskResponse updateTaskStatus(UUID tenantId, UUID projectId, UUID taskId, UpdateTaskStatusRequest request) {
		Task task = findTask(tenantId, projectId, taskId);
		if (task.getStatus() == request.status()) {
			// Repeated request (e.g. a retried Kanban drop): nothing changes, nothing is recorded.
			return withBlockers(task);
		}
		if (request.status() == TaskStatus.DONE) {
			dependencyService.requireUnblocked(tenantId, task);
		}
		TaskStatus previous = task.getStatus();
		task.setStatus(request.status());
		Task saved = taskRepository.saveAndFlush(task);
		taskActivity.statusChanged(tenantId, saved);
		afterChange(tenantId, previous, saved.getPriority(), saved);
		cacheInvalidator.invalidate(tenantId);
		return withBlockers(saved);
	}

	@Transactional
	public void deleteTask(UUID tenantId, UUID projectId, UUID taskId) {
		Task task = findTask(tenantId, projectId, taskId);
		taskActivity.deleted(tenantId, task);
		attachmentService.deleteAllForTask(task.getId());
		commentRepository.deleteAllByTaskId(task.getId());
		dependencyRepository.deleteAllForTask(task.getId());
		focusRepository.deleteAllByTaskId(task.getId());
		taskRepository.delete(task);
		cacheInvalidator.invalidate(tenantId);
	}

	/** Side effects of a saved change: the next occurrence of a recurring task, and automations. */
	private void afterChange(UUID tenantId, TaskStatus previousStatus, io.github.aryan52.tenantflow.entity.TaskPriority previousPriority, Task saved) {
		if (previousStatus != TaskStatus.DONE && saved.getStatus() == TaskStatus.DONE) {
			createNextOccurrence(tenantId, saved);
			automationService.onTaskCompleted(tenantId, saved);
		}
		if (AutomationService.becameUrgent(previousPriority, saved.getPriority())) {
			automationService.onUrgent(tenantId, saved);
		}
	}

	/**
	 * Completing an occurrence of a recurring task creates the next one, once: the series and
	 * the next due date identify it (unique in the database), so completing, reopening and
	 * completing again never creates a duplicate. Earlier occurrences are never changed.
	 */
	private void createNextOccurrence(UUID tenantId, Task done) {
		if (done.getRecurrenceFrequency() == null || done.getSeriesId() == null) {
			return;
		}
		Recurrences.next(done.getDueDate(), done.getRecurrenceFrequency(),
				done.getRecurrenceInterval() == null ? 1 : done.getRecurrenceInterval(),
				done.getRecurrenceWeekdays() == null ? 0 : done.getRecurrenceWeekdays(), done.getRecurrenceUntil())
				.filter(nextDue -> !taskRepository.existsBySeriesIdAndDueDate(done.getSeriesId(), nextDue))
				.ifPresent(nextDue -> {
					Integer span = done.getStartDate() == null || done.getDueDate() == null ? null
							: (int) java.time.temporal.ChronoUnit.DAYS.between(done.getStartDate(), done.getDueDate());
					Task next = taskRepository.saveAndFlush(Task.builder()
							.project(done.getProject())
							.title(done.getTitle())
							.description(done.getDescription())
							.status(TaskStatus.TODO)
							.priority(done.getPriority())
							.assignee(done.getAssignee())
							.createdBy(done.getCreatedBy())
							.dueDate(nextDue)
							.startDate(span == null ? null : nextDue.minusDays(span))
							.milestoneId(null)
							.recurrenceFrequency(done.getRecurrenceFrequency())
							.recurrenceInterval(done.getRecurrenceInterval())
							.recurrenceWeekdays(done.getRecurrenceWeekdays())
							.recurrenceUntil(done.getRecurrenceUntil())
							.seriesId(done.getSeriesId())
							.build());
					taskActivity.created(tenantId, next);
				});
	}

	/** Applies a {@link TaskPlanning} (replace semantics) after validating it. */
	private void applyPlanning(UUID tenantId, UUID projectId, Task task, TaskPlanning planning) {
		if (planning == null) {
			validateDates(task);
			return;
		}
		task.setStartDate(planning.startDate());
		if (planning.milestoneId() != null) {
			milestoneService.requireInProject(tenantId, projectId, planning.milestoneId());
		}
		task.setMilestoneId(planning.milestoneId());
		TaskPlanning.Recurrence rec = planning.recurrence();
		if (rec == null) {
			task.setRecurrenceFrequency(null);
			task.setRecurrenceInterval(null);
			task.setRecurrenceWeekdays(null);
			task.setRecurrenceUntil(null);
			// The series id stays: it still identifies the earlier occurrences.
		} else {
			if (task.getDueDate() == null) {
				throw new IllegalArgumentException("A recurring task needs a due date");
			}
			if (rec.until() != null && rec.until().isBefore(task.getDueDate())) {
				throw new IllegalArgumentException("The repeat end date must be on or after the due date");
			}
			task.setRecurrenceFrequency(rec.frequency());
			task.setRecurrenceInterval(rec.interval() == null ? 1 : rec.interval());
			task.setRecurrenceWeekdays(rec.frequency() == io.github.aryan52.tenantflow.entity.RecurrenceFrequency.WEEKLY && rec.weekdays() != null
					? rec.weekdays().shortValue() : null);
			task.setRecurrenceUntil(rec.until());
			if (task.getSeriesId() == null) {
				task.setSeriesId(UUID.randomUUID());
			}
		}
		validateDates(task);
	}

	private static void validateDates(Task task) {
		if (task.getStartDate() != null && task.getDueDate() != null && task.getDueDate().isBefore(task.getStartDate())) {
			throw new IllegalArgumentException("The due date must be on or after the start date");
		}
	}

	private Project findProject(UUID tenantId, UUID projectId) {
		return projectRepository.findByIdAndTenant_Id(projectId, tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Project"));
	}

	private Task findTask(UUID tenantId, UUID projectId, UUID taskId) {
		findProject(tenantId, projectId);
		return taskRepository.findByIdAndProject_Id(taskId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Task"));
	}

	/**
	 * New assignees must be members of the tenant. Keeping the current assignee unchanged
	 * is always allowed, so a task assigned to someone who has since left the workspace
	 * can still be edited without being forced to reassign it.
	 */
	private User resolveAssignee(UUID tenantId, UUID assigneeId, User currentAssignee) {
		if (assigneeId == null) {
			return null;
		}
		if (currentAssignee != null && Objects.equals(currentAssignee.getId(), assigneeId)) {
			return currentAssignee;
		}
		if (!membershipRepository.existsByUser_IdAndTenant_Id(assigneeId, tenantId)) {
			throw new IllegalArgumentException("Assignee must be a member of the tenant");
		}
		return userRepository.findById(assigneeId)
				.orElseThrow(() -> new ResourceNotFoundException("User"));
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
