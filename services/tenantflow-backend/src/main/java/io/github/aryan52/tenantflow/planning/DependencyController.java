package io.github.aryan52.tenantflow.planning;

import io.github.aryan52.tenantflow.planning.DependencyService.Dependencies;
import io.github.aryan52.tenantflow.planning.DependencyService.TaskLookup;
import io.github.aryan52.tenantflow.security.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/{tenantId}/tasks")
@RequiredArgsConstructor
public class DependencyController {

	public record AddDependencyRequest(UUID blockingTaskId) {
	}

	private final DependencyService dependencyService;

	@GetMapping("/{taskId}/dependencies")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public Dependencies get(@PathVariable UUID tenantId, @PathVariable UUID taskId) {
		return dependencyService.get(tenantId, taskId);
	}

	@PostMapping("/{taskId}/dependencies")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public Dependencies add(@PathVariable UUID tenantId, @PathVariable UUID taskId,
			@AuthenticationPrincipal AuthenticatedUser user, @RequestBody AddDependencyRequest request) {
		return dependencyService.add(tenantId, taskId, request.blockingTaskId(), user.getId());
	}

	@DeleteMapping("/{taskId}/dependencies/{dependencyId}")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public Dependencies remove(@PathVariable UUID tenantId, @PathVariable UUID taskId, @PathVariable UUID dependencyId) {
		return dependencyService.remove(tenantId, taskId, dependencyId);
	}

	/** Title search inside this workspace for the dependency picker. */
	@GetMapping("/lookup")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public List<TaskLookup> lookup(@PathVariable UUID tenantId, @RequestParam(defaultValue = "") String q) {
		return dependencyService.lookup(tenantId, q);
	}
}
