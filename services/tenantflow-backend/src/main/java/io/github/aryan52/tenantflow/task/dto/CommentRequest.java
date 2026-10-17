package io.github.aryan52.tenantflow.task.dto;

import io.github.aryan52.tenantflow.task.TaskComment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(
		@NotBlank(message = "Comment cannot be empty")
		@Size(max = TaskComment.BODY_MAX, message = "Comment must be at most 4000 characters")
		String body,
		/** Members mentioned as "@Name" in the body; each is notified once per comment. */
		@Size(max = 20, message = "At most 20 people can be mentioned in one comment")
		java.util.List<java.util.UUID> mentionedUserIds
) {
	public CommentRequest(String body) {
		this(body, null);
	}
}
