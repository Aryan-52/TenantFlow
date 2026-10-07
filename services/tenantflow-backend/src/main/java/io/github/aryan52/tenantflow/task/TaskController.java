package io.github.aryan52.tenantflow.task;

import io.github.aryan52.tenantflow.task.dto.CreateTaskRequest;
import io.github.aryan52.tenantflow.task.dto.TaskResponse;
import io.github.aryan52.tenantflow.task.dto.UpdateTaskRequest;
import io.github.aryan52.tenantflow.task.dto.UpdateTaskStatusRequest;
import io.github.aryan52.tenantflow.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/{tenantId}/projects/{projectId}/tasks")
@RequiredArgsConstructor
public class TaskController {

	private final TaskService taskService;

	@GetMapping
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public List<TaskResponse> getTasks(
			@PathVariable UUID tenantId,
			@PathVariable UUID projectId
	) {
		return taskService.getTasks(tenantId, projectId);
	}

	@GetMapping("/{taskId}")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public TaskResponse getTask(
			@PathVariable UUID tenantId,
			@PathVariable UUID projectId,
			@PathVariable UUID taskId
	) {
		return taskService.getTask(tenantId, projectId, taskId);
	}

	@PostMapping
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public ResponseEntity<TaskResponse> createTask(
			@PathVariable UUID tenantId,
			@PathVariable UUID projectId,
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Valid @RequestBody CreateTaskRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(taskService.createTask(tenantId, projectId, currentUser.getId(), request));
	}

	@PutMapping("/{taskId}")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public TaskResponse updateTask(
			@PathVariable UUID tenantId,
			@PathVariable UUID projectId,
			@PathVariable UUID taskId,
			@Valid @RequestBody UpdateTaskRequest request
	) {
		return taskService.updateTask(tenantId, projectId, taskId, request);
	}

	/** Narrow status-only update (Kanban moves, quick status changes) that cannot clobber
	 * concurrent edits to the task's other fields. */
	@PatchMapping("/{taskId}/status")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public TaskResponse updateTaskStatus(
			@PathVariable UUID tenantId,
			@PathVariable UUID projectId,
			@PathVariable UUID taskId,
			@Valid @RequestBody UpdateTaskStatusRequest request
	) {
		return taskService.updateTaskStatus(tenantId, projectId, taskId, request);
	}

	@DeleteMapping("/{taskId}")
	@PreAuthorize("@tenantSecurity.hasRole(#tenantId, 'ADMIN', 'OWNER')")
	public ResponseEntity<Void> deleteTask(
			@PathVariable UUID tenantId,
			@PathVariable UUID projectId,
			@PathVariable UUID taskId
	) {
		taskService.deleteTask(tenantId, projectId, taskId);
		return ResponseEntity.noContent().build();
	}
}
