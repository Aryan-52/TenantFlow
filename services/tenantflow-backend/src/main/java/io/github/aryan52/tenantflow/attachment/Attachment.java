package io.github.aryan52.tenantflow.attachment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Attachment metadata; the bytes live in object storage under {@link #storageKey}. */
@Entity
@Table(name = "attachments")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Attachment {

	/** Assigned by the application before upload, so the storage key can include it. */
	@Id
	@Column(nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID id;

	@Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
	private UUID tenantId;

	@Column(name = "project_id", nullable = false, columnDefinition = "uuid")
	private UUID projectId;

	@Column(name = "task_id", columnDefinition = "uuid")
	private UUID taskId;

	@Column(name = "uploaded_by", columnDefinition = "uuid")
	private UUID uploadedBy;

	@Column(name = "original_filename", nullable = false, length = 255)
	private String originalFilename;

	@Column(name = "content_type", nullable = false, length = 127)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	@lombok.Setter
	private long sizeBytes;

	@Column(nullable = false, length = 64)
	@lombok.Setter
	private String sha256;

	@Column(name = "storage_key", nullable = false, length = 500, unique = true)
	private String storageKey;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}
