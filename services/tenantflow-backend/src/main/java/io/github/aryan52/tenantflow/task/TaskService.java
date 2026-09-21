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
import java.util.List;
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
		Project project = projectRepository.findByIdAndTenant_Id(projectId, tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Project"));
		return taskRepository.findByProject_Id(projectId).stream()
				.map(TaskResponse::from)
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public TaskResponse getTask(UUID tenantId, UUID projectId, UUID taskId) {
		Project project = projectRepository.findByIdAndTenant_Id(projectId, tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Project"));
		Task task = taskRepository.findByIdAndProject_Id(taskId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Task"));
		return TaskResponse.from(task);
	}

	@Transactional
	public TaskResponse createTask(UUID tenantId, UUID projectId, CreateTaskRequest request) {
		Project project = projectRepository.findByIdAndTenant_Id(projectId, tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Project"));

		User assignee = null;
		if (request.assigneeId() != null) {
			membershipRepository.findByUser_IdAndTenant_Id(request.assigneeId(), tenantId)
					.orElseThrow(() -> new IllegalArgumentException("Assignee must be a member of the tenant"));
			assignee = userRepository.findById(request.assigneeId())
					.orElseThrow(() -> new ResourceNotFoundException("User"));
		}

		Task task = Task.builder()
				.project(project)
				.title(request.title().trim())
				.description(request.description() != null ? request.description().trim() : null)
				.status(request.status())
				.priority(request.priority())
				.assignee(assignee)
				.build();

		return TaskResponse.from(taskRepository.save(task));
	}

	@Transactional
	public TaskResponse updateTask(UUID tenantId, UUID projectId, UUID taskId, UpdateTaskRequest request) {
		Project project = projectRepository.findByIdAndTenant_Id(projectId, tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Project"));
		Task task = taskRepository.findByIdAndProject_Id(taskId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Task"));

		User assignee = null;
		if (request.assigneeId() != null) {
			membershipRepository.findByUser_IdAndTenant_Id(request.assigneeId(), tenantId)
					.orElseThrow(() -> new IllegalArgumentException("Assignee must be a member of the tenant"));
			assignee = userRepository.findById(request.assigneeId())
					.orElseThrow(() -> new ResourceNotFoundException("User"));
		}

		task.setTitle(request.title().trim());
		task.setDescription(request.description() != null ? request.description().trim() : null);
		task.setStatus(request.status());
		task.setPriority(request.priority());
		task.setAssignee(assignee);

		return TaskResponse.from(taskRepository.save(task));
	}

	@Transactional
	public void deleteTask(UUID tenantId, UUID projectId, UUID taskId) {
		Project project = projectRepository.findByIdAndTenant_Id(projectId, tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Project"));
		Task task = taskRepository.findByIdAndProject_Id(taskId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Task"));
		taskRepository.delete(task);
	}
}
