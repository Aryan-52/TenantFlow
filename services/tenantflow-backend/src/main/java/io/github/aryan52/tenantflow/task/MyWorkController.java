package io.github.aryan52.tenantflow.task;

import io.github.aryan52.tenantflow.security.AuthenticatedUser;
import io.github.aryan52.tenantflow.task.MyWorkService.MyWork;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP entry point of My Work; see {@link MyWorkService}. */
@RestController
@RequestMapping("/api/tenants/{tenantId}/my-work")
@RequiredArgsConstructor
public class MyWorkController {

	private final MyWorkService myWorkService;

	@GetMapping
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public MyWork get(@PathVariable UUID tenantId, @AuthenticationPrincipal AuthenticatedUser user) {
		return myWorkService.get(tenantId, user.getId());
	}

	@PutMapping("/focus/{taskId}")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public ResponseEntity<Void> focus(@PathVariable UUID tenantId, @PathVariable UUID taskId,
			@AuthenticationPrincipal AuthenticatedUser user) {
		myWorkService.focus(tenantId, user.getId(), taskId);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/focus/{taskId}")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public ResponseEntity<Void> unfocus(@PathVariable UUID tenantId, @PathVariable UUID taskId,
			@AuthenticationPrincipal AuthenticatedUser user) {
		myWorkService.unfocus(tenantId, user.getId(), taskId);
		return ResponseEntity.noContent().build();
	}
}
