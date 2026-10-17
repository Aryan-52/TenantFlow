package io.github.aryan52.tenantflow.attachment.dto;

import io.github.aryan52.tenantflow.attachment.Attachment;
import java.time.Instant;
import java.util.UUID;

/** The storage key is internal and never exposed. */
public record AttachmentResponse(
		UUID id,
		UUID projectId,
		UUID taskId,
		String filename,
		String contentType,
		long sizeBytes,
		UUID uploadedById,
		String uploadedByName,
		Instant createdAt
) {
	public static AttachmentResponse from(Attachment a, String uploaderName) {
		return new AttachmentResponse(a.getId(), a.getProjectId(), a.getTaskId(), a.getOriginalFilename(),
				a.getContentType(), a.getSizeBytes(), a.getUploadedBy(), uploaderName, a.getCreatedAt());
	}
}
