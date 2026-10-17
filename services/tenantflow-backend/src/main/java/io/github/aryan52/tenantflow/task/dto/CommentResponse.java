package io.github.aryan52.tenantflow.task.dto;

import io.github.aryan52.tenantflow.task.TaskComment;
import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
		UUID id,
		UUID taskId,
		UUID authorId,
		/** Null when the author's account no longer exists. */
		String authorName,
		String body,
		Instant createdAt,
		Instant updatedAt,
		boolean edited
) {
	public static CommentResponse from(TaskComment c, String authorName) {
		// Saving sets both timestamps together; any later edit moves updatedAt.
		boolean edited = c.getUpdatedAt() != null && c.getCreatedAt() != null
				&& c.getUpdatedAt().isAfter(c.getCreatedAt().plusSeconds(1));
		return new CommentResponse(c.getId(), c.getTaskId(), c.getAuthorId(), authorName, c.getBody(),
				c.getCreatedAt(), c.getUpdatedAt(), edited);
	}
}
