package io.github.aryan52.tenantflow.attachment;

import io.github.aryan52.tenantflow.activity.ActivityService;
import io.github.aryan52.tenantflow.attachment.dto.AttachmentResponse;
import io.github.aryan52.tenantflow.audit.AuditAction;
import io.github.aryan52.tenantflow.audit.AuditService;
import io.github.aryan52.tenantflow.audit.AuditService.Entry;
import io.github.aryan52.tenantflow.common.PageResponse;
import io.github.aryan52.tenantflow.common.Pagination;
import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.Project;
import io.github.aryan52.tenantflow.entity.Role;
import io.github.aryan52.tenantflow.entity.Task;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.error.ForbiddenOperationException;
import io.github.aryan52.tenantflow.error.ConflictException;
import io.github.aryan52.tenantflow.storage.StoredObjectMissingException;
import io.github.aryan52.tenantflow.error.ResourceNotFoundException;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import io.github.aryan52.tenantflow.repository.TaskRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.storage.ObjectStorage;
import io.github.aryan52.tenantflow.storage.StorageProperties;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Project and task attachments. Every operation is scoped by tenant + project (+ task),
 * which the controller has already authorised; ids from the client are never trusted on
 * their own. Bytes go to object storage under a server-generated key; Postgres holds the
 * metadata. The two stores are kept consistent with compensation on upload and
 * after-commit deletion of objects.
 */
@Service
public class AttachmentService {

	private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);
	private static final int SNIFF_BYTES = 8192;

	private final AttachmentRepository attachmentRepository;
	private final ProjectRepository projectRepository;
	private final TaskRepository taskRepository;
	private final MembershipRepository membershipRepository;
	private final UserRepository userRepository;
	private final ObjectProvider<ObjectStorage> storage;
	private final StorageProperties storageProperties;
	private final ActivityService activityService;
	private final AuditService auditService;

	public AttachmentService(AttachmentRepository attachmentRepository, ProjectRepository projectRepository,
			TaskRepository taskRepository, MembershipRepository membershipRepository, UserRepository userRepository,
			ObjectProvider<ObjectStorage> storage, StorageProperties storageProperties, ActivityService activityService,
			AuditService auditService) {
		this.attachmentRepository = attachmentRepository;
		this.projectRepository = projectRepository;
		this.taskRepository = taskRepository;
		this.membershipRepository = membershipRepository;
		this.userRepository = userRepository;
		this.storage = storage;
		this.storageProperties = storageProperties;
		this.activityService = activityService;
		this.auditService = auditService;
	}

	public record Download(Attachment attachment, InputStream content) {
	}

	@Transactional
	public AttachmentResponse upload(UUID tenantId, UUID projectId, UUID taskId, UUID userId, MultipartFile file) {
		Project project = findProject(tenantId, projectId);
		Task task = taskId == null ? null : findTask(projectId, taskId);

		String filename = FileTypePolicy.sanitizeFilename(file == null ? null : file.getOriginalFilename());
		UUID id = UUID.randomUUID();
		String key = "tenants/" + tenantId + "/projects/" + projectId + "/" + id;
		StoredFile stored = validateAndStore(key, filename, file, null);
		String contentType = stored.contentType();
		String sha256 = stored.sha256();

		Attachment saved = attachmentRepository.saveAndFlush(Attachment.builder()
				.id(id)
				.tenantId(tenantId)
				.projectId(projectId)
				.taskId(task == null ? null : task.getId())
				.uploadedBy(userId)
				.originalFilename(filename)
				.contentType(contentType)
				.sizeBytes(file.getSize())
				.sha256(sha256)
				.storageKey(key)
				.build());

		activityService.record(tenantId, projectId, task == null ? null : task.getId(), "ATTACHMENT_ADDED",
				"attached " + quote(filename) + " to " + (task == null ? "project " + quote(project.getName()) : quote(task.getTitle())),
				List.of());
		auditService.record(Entry.success(AuditAction.ATTACHMENT_UPLOADED, tenantId, "ATTACHMENT", id,
				"size=" + file.getSize() + " type=" + contentType));
		return AttachmentResponse.from(saved, userName(userId));
	}

	@Transactional(readOnly = true)
	public PageResponse<AttachmentResponse> list(UUID tenantId, UUID projectId, UUID taskId, Integer page, Integer size) {
		findProject(tenantId, projectId);
		var pageable = Pagination.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
		Page<Attachment> result = taskId == null
				? attachmentRepository.findByTenantIdAndProjectId(tenantId, projectId, pageable)
				: attachmentRepository.findByTenantIdAndProjectIdAndTaskId(tenantId, projectId, taskId, pageable);

		var uploaderIds = result.getContent().stream().map(Attachment::getUploadedBy).filter(Objects::nonNull).collect(Collectors.toSet());
		Map<UUID, String> names = userRepository.findAllById(uploaderIds).stream()
				.collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));
		Function<Attachment, AttachmentResponse> mapper = a -> AttachmentResponse.from(a, names.get(a.getUploadedBy()));
		return PageResponse.of(result, mapper);
	}

	@Transactional(readOnly = true)
	public Download download(UUID tenantId, UUID projectId, UUID attachmentId) {
		Attachment attachment = find(tenantId, projectId, attachmentId);
		return new Download(attachment, storage.getObject().get(attachment.getStorageKey()));
	}

	/**
	 * Puts content back into a file record whose stored bytes are missing, keeping the record,
	 * its name and its history. Allowed only while the bytes are really missing (existing
	 * content is never overwritten) and only for the uploader or a workspace admin. The new
	 * file must be the same type; the activity says whether it is byte-for-byte the original
	 * (same SHA-256) or a replacement, so nobody is told a file was "recovered" when it was not.
	 */
	@Transactional
	public AttachmentResponse restoreContent(UUID tenantId, UUID projectId, UUID attachmentId, UUID userId, MultipartFile file) {
		Attachment attachment = find(tenantId, projectId, attachmentId);
		requireUploaderOrAdmin(attachment, tenantId, userId, "Only the uploader or a workspace admin can restore this file");
		if (contentAvailable(attachment.getStorageKey())) {
			throw new ConflictException("This file's contents are still available, so there is nothing to restore.");
		}
		String filename = FileTypePolicy.sanitizeFilename(file == null ? null : file.getOriginalFilename());
		StoredFile stored = validateAndStore(attachment.getStorageKey(), filename, file, attachment.getContentType());
		boolean original = stored.sha256().equals(attachment.getSha256());
		attachment.setSizeBytes(file.getSize());
		attachment.setSha256(stored.sha256());
		Attachment saved = attachmentRepository.saveAndFlush(attachment);

		activityService.record(tenantId, projectId, attachment.getTaskId(), "ATTACHMENT_RESTORED",
				(original ? "restored the original contents of " : "uploaded replacement contents for ")
						+ quote(attachment.getOriginalFilename()), List.of());
		auditService.record(Entry.success(AuditAction.ATTACHMENT_UPLOADED, tenantId, "ATTACHMENT", attachmentId,
				"restore size=" + file.getSize() + " identical=" + original));
		return AttachmentResponse.from(saved, userName(attachment.getUploadedBy()));
	}

	/** Uploader, or a workspace ADMIN/OWNER, may delete. */
	@Transactional
	public void delete(UUID tenantId, UUID projectId, UUID attachmentId, UUID userId) {
		Attachment attachment = find(tenantId, projectId, attachmentId);
		requireUploaderOrAdmin(attachment, tenantId, userId, "Only the uploader or a workspace admin can delete this file");
		attachmentRepository.delete(attachment);
		deleteObjectsAfterCommit(List.of(attachment.getStorageKey()));
		activityService.record(tenantId, projectId, attachment.getTaskId(), "ATTACHMENT_REMOVED",
				"removed " + quote(attachment.getOriginalFilename()), List.of());
		auditService.record(Entry.success(AuditAction.ATTACHMENT_DELETED, tenantId, "ATTACHMENT", attachmentId,
				"name=" + attachment.getOriginalFilename()));
	}

	// ----------------------------------------------------------------- cascade helpers

	/** Called before a task is deleted: removes its attachment rows now and objects after commit. */
	@Transactional
	public void deleteAllForTask(UUID taskId) {
		List<String> keys = attachmentRepository.storageKeysForTask(taskId);
		attachmentRepository.deleteAllByTaskId(taskId);
		deleteObjectsAfterCommit(keys);
	}

	@Transactional
	public void deleteAllForProject(UUID projectId) {
		List<String> keys = attachmentRepository.storageKeysForProject(projectId);
		attachmentRepository.deleteAllByProjectId(projectId);
		deleteObjectsAfterCommit(keys);
	}

	@Transactional
	public void deleteAllForTenant(UUID tenantId) {
		List<String> keys = attachmentRepository.storageKeysForTenant(tenantId);
		attachmentRepository.deleteAllByTenantId(tenantId);
		deleteObjectsAfterCommit(keys);
	}

	// ----------------------------------------------------------------- internals

	private record StoredFile(String contentType, String sha256) {
	}

	/**
	 * The one place a file is checked and written: size limit, name, real type from its first
	 * bytes (and, when {@code expectedType} is given, that it matches), then the bytes are
	 * streamed to storage under {@code key} while their SHA-256 is computed. If the surrounding
	 * transaction does not commit, the written object is removed again.
	 */
	private StoredFile validateAndStore(String key, String filename, MultipartFile file, String expectedType) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("Choose a file to upload");
		}
		if (file.getSize() > storageProperties.maxFileSize().toBytes()) {
			throw new IllegalArgumentException("The file is larger than the " + storageProperties.maxFileSize().toMegabytes() + " MB limit");
		}
		try (InputStream raw = file.getInputStream()) {
			BufferedInputStream buffered = new BufferedInputStream(raw, SNIFF_BYTES);
			buffered.mark(SNIFF_BYTES);
			byte[] head = buffered.readNBytes(SNIFF_BYTES);
			buffered.reset();
			String contentType = FileTypePolicy.validate(filename, head);
			if (expectedType != null && !expectedType.equals(contentType)) {
				throw new IllegalArgumentException("Choose a file of the same type as the original ("
						+ FileTypePolicy.label(expectedType) + ").");
			}
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (DigestInputStream hashing = new DigestInputStream(buffered, digest)) {
				storage.getObject().put(key, hashing, file.getSize(), contentType);
			}
			deleteObjectsUnlessCommitted(List.of(key));
			return new StoredFile(contentType, HexFormat.of().formatHex(digest.digest()));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** True when storage still has bytes for the key (a missing object is not an outage). */
	private boolean contentAvailable(String key) {
		try (InputStream ignored = storage.getObject().get(key)) {
			return true;
		} catch (StoredObjectMissingException e) {
			return false;
		} catch (IOException e) {
			return true; // it opened, so it exists
		}
	}

	private void requireUploaderOrAdmin(Attachment attachment, UUID tenantId, UUID userId, String message) {
		Role role = membershipRepository.findByUser_IdAndTenant_Id(userId, tenantId).map(Membership::getRole)
				.orElseThrow(() -> new ResourceNotFoundException("Attachment"));
		if (!userId.equals(attachment.getUploadedBy()) && !role.atLeast(Role.ADMIN)) {
			throw new ForbiddenOperationException(message);
		}
	}

	private Attachment find(UUID tenantId, UUID projectId, UUID attachmentId) {
		return attachmentRepository.findByIdAndTenantIdAndProjectId(attachmentId, tenantId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Attachment"));
	}

	private Project findProject(UUID tenantId, UUID projectId) {
		return projectRepository.findByIdAndTenant_Id(projectId, tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Project"));
	}

	private Task findTask(UUID projectId, UUID taskId) {
		return taskRepository.findByIdAndProject_Id(taskId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Task"));
	}

	private String userName(UUID userId) {
		return userRepository.findById(userId).map(User::getName).orElse(null);
	}

	/** Objects are only removed once the metadata deletion has committed (a rollback keeps the file). */
	private void deleteObjectsAfterCommit(Collection<String> keys) {
		if (keys.isEmpty()) {
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				deleteQuietly(keys);
			}
		});
	}

	/** Compensation for an upload whose metadata never committed. */
	private void deleteObjectsUnlessCommitted(Collection<String> keys) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) {
					deleteQuietly(keys);
				}
			}
		});
	}

	private void deleteQuietly(Collection<String> keys) {
		for (String key : keys) {
			try {
				storage.getObject().delete(key);
			} catch (RuntimeException ex) {
				// Metadata is gone, so the object is unreachable through the API; log it for cleanup.
				log.warn("Could not delete stored object {} ({})", key, ex.getClass().getSimpleName());
			}
		}
	}

	private static String quote(String s) {
		return "\"" + s + "\"";
	}
}
