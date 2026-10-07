package io.github.aryan52.tenantflow.task;

import io.github.aryan52.tenantflow.entity.Project;
import io.github.aryan52.tenantflow.entity.Task;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.error.ResourceNotFoundException;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import io.github.aryan52.tenantflow.repository.TaskRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.task.dto.CreateTaskRequest;
import io.github.aryan52.tenantflow.task.dto.TaskResponse;
import io.github.aryan52.tenantflow.task.dto.UpdateTaskRequest;
import io.github.aryan52.tenantflow.task.dto.UpdateTaskStatusRequest;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskService {

	private final TaskRepository taskRepository;
	private final ProjectRepository projectRepository;
	private final UserRepository userRepository;
	private final MembershipRepository membershipRepository;

	@Transactional(readOnly = true)
	public List<TaskResponse> getTasks(UUID tenantId, UUID projectId) {
		findProject(tenantId, projectId);
		return taskRepository.findByProject_IdOrderByCreatedAtAsc(projectId).stream()
				.map(TaskResponse::from)
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public TaskResponse getTask(UUID tenantId, UUID projectId, UUID taskId) {
		return TaskResponse.from(findTask(tenantId, projectId, taskId));
	}

	@Transactional
	public TaskResponse createTask(UUID tenantId, UUID projectId, UUID currentUserId, CreateTaskRequest request) {
		Project project = findProject(tenantId, projectId);

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

		return TaskResponse.from(taskRepository.save(task));
	}

	@Transactional
	public TaskResponse updateTask(UUID tenantId, UUID projectId, UUID taskId, UpdateTaskRequest request) {
		Task task = findTask(tenantId, projectId, taskId);

		if (request.projectId() != null && !request.projectId().equals(projectId)) {
			// Target is looked up within the same tenant, so a task can never be moved
			// into (or reveal the existence of) another workspace's project.
			task.setProject(findProject(tenantId, request.projectId()));
		}

		task.setTitle(request.title().trim());
		task.setDescription(trimToNull(request.description()));
		task.setStatus(request.status());
		task.setPriority(request.priority());
		task.setAssignee(resolveAssignee(tenantId, request.assigneeId(), task.getAssignee()));
		task.setDueDate(request.dueDate());

		return TaskResponse.from(taskRepository.save(task));
	}

	@Transactional
	public TaskResponse updateTaskStatus(UUID tenantId, UUID projectId, UUID taskId, UpdateTaskStatusRequest request) {
		Task task = findTask(tenantId, projectId, taskId);
		task.setStatus(request.status());
		return TaskResponse.from(taskRepository.save(task));
	}

	@Transactional
	public void deleteTask(UUID tenantId, UUID projectId, UUID taskId) {
		taskRepository.delete(findTask(tenantId, projectId, taskId));
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
