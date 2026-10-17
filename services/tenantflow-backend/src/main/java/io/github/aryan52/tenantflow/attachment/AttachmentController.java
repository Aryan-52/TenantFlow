package io.github.aryan52.tenantflow.attachment;

import io.github.aryan52.tenantflow.attachment.dto.AttachmentResponse;
import io.github.aryan52.tenantflow.common.PageResponse;
import io.github.aryan52.tenantflow.security.AuthenticatedUser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Attachments of a project (optionally narrowed to one of its tasks). Members only. */
@RestController
@RequestMapping("/api/tenants/{tenantId}/projects/{projectId}/attachments")
@RequiredArgsConstructor
public class AttachmentController {

	private final AttachmentService attachmentService;

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public ResponseEntity<AttachmentResponse> upload(
			@PathVariable UUID tenantId,
			@PathVariable UUID projectId,
			@RequestParam(required = false) UUID taskId,
			@RequestPart("file") MultipartFile file,
			@AuthenticationPrincipal AuthenticatedUser user
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(attachmentService.upload(tenantId, projectId, taskId, user.getId(), file));
	}

	@GetMapping
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public PageResponse<AttachmentResponse> list(
			@PathVariable UUID tenantId,
			@PathVariable UUID projectId,
			@RequestParam(required = false) UUID taskId,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size
	) {
		return attachmentService.list(tenantId, projectId, taskId, page, size);
	}

	/**
	 * Streams the file through the API after the membership check - there are no public
	 * object URLs. Always served as a download (never rendered inline) so an uploaded file
	 * can never execute in the application's origin.
	 */
	@GetMapping("/{attachmentId}/download")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public ResponseEntity<InputStreamResource> download(
			@PathVariable UUID tenantId,
			@PathVariable UUID projectId,
			@PathVariable UUID attachmentId
	) {
		AttachmentService.Download d = attachmentService.download(tenantId, projectId, attachmentId);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(d.attachment().getContentType()))
				.contentLength(d.attachment().getSizeBytes())
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename(d.attachment().getOriginalFilename(), java.nio.charset.StandardCharsets.UTF_8)
						.build().toString())
				.cacheControl(CacheControl.noStore().cachePrivate())
				.body(new InputStreamResource(d.content()));
	}

	/** Restores the bytes of a file whose stored contents are missing (see AttachmentService#restoreContent). */
	@PutMapping(value = "/{attachmentId}/content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public AttachmentResponse restoreContent(
			@PathVariable UUID tenantId,
			@PathVariable UUID projectId,
			@PathVariable UUID attachmentId,
			@RequestPart("file") MultipartFile file,
			@AuthenticationPrincipal AuthenticatedUser user
	) {
		return attachmentService.restoreContent(tenantId, projectId, attachmentId, user.getId(), file);
	}

	@DeleteMapping("/{attachmentId}")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public ResponseEntity<Void> delete(
			@PathVariable UUID tenantId,
			@PathVariable UUID projectId,
			@PathVariable UUID attachmentId,
			@AuthenticationPrincipal AuthenticatedUser user
	) {
		attachmentService.delete(tenantId, projectId, attachmentId, user.getId());
		return ResponseEntity.noContent().build();
	}
}
