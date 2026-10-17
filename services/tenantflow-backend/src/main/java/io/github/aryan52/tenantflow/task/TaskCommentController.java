package io.github.aryan52.tenantflow.task;

import io.github.aryan52.tenantflow.common.PageResponse;
import io.github.aryan52.tenantflow.security.AuthenticatedUser;
import io.github.aryan52.tenantflow.task.dto.CommentRequest;
import io.github.aryan52.tenantflow.task.dto.CommentResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A task's discussion thread. Workspace members only; edit/delete rules in the service. */
@RestController
@RequestMapping("/api/tenants/{tenantId}/projects/{projectId}/tasks/{taskId}/comments")
@RequiredArgsConstructor
public class TaskCommentController {

	private final TaskCommentService commentService;

	@GetMapping
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public PageResponse<CommentResponse> list(
			@PathVariable UUID tenantId,
			@PathVariable UUID projectId,
			@PathVariable UUID taskId,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size
	) {
		return commentService.list(tenantId, projectId, taskId, page, size);
	}

	@PostMapping
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public ResponseEntity<CommentResponse> create(
			@PathVariable UUID tenantId,
			@PathVariable UUID projectId,
			@PathVariable UUID taskId,
			@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody CommentRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(commentService.create(tenantId, projectId, taskId, user.getId(), request.body(), request.mentionedUserIds()));
	}

	@PutMapping("/{commentId}")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public CommentResponse update(
			@PathVariable UUID tenantId,
			@PathVariable UUID projectId,
			@PathVariable UUID taskId,
			@PathVariable UUID commentId,
			@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody CommentRequest request
	) {
		return commentService.update(tenantId, projectId, taskId, commentId, user.getId(), request.body(), request.mentionedUserIds());
	}

	@DeleteMapping("/{commentId}")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public ResponseEntity<Void> delete(
			@PathVariable UUID tenantId,
			@PathVariable UUID projectId,
			@PathVariable UUID taskId,
			@PathVariable UUID commentId,
			@AuthenticationPrincipal AuthenticatedUser user
	) {
		commentService.delete(tenantId, projectId, taskId, commentId, user.getId());
		return ResponseEntity.noContent().build();
	}
}
