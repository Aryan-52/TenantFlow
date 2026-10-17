package io.github.aryan52.tenantflow.attachment;

import io.github.aryan52.tenantflow.attachment.DocumentService.DocumentResponse;
import io.github.aryan52.tenantflow.common.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP entry point of the document center; see {@link DocumentService}. */
@RestController
@RequestMapping("/api/tenants/{tenantId}/documents")
@RequiredArgsConstructor
public class DocumentController {

	private final DocumentService documentService;

	@GetMapping
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public PageResponse<DocumentResponse> list(@PathVariable UUID tenantId,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) UUID projectId,
			@RequestParam(required = false) String type,
			@RequestParam(required = false) UUID uploadedBy,
			@RequestParam(defaultValue = "newest") String sort,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size) {
		return documentService.list(tenantId, q, projectId, type, uploadedBy, sort, page, size);
	}
}
