package io.github.aryan52.tenantflow.task;

import io.github.aryan52.tenantflow.common.PageResponse;
import io.github.aryan52.tenantflow.security.CurrentUser;
import io.github.aryan52.tenantflow.task.dto.TaskResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Open tasks across every project of a workspace, filtered by a dashboard focus. */
@RestController
@RequestMapping("/api/tenants/{tenantId}/tasks")
@RequiredArgsConstructor
public class TenantTaskController {

	private final TaskService taskService;

	@GetMapping
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public PageResponse<TaskResponse> getTenantTasks(
			@PathVariable UUID tenantId,
			@RequestParam(required = false) String focus,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size
	) {
		return taskService.getTenantTasks(tenantId, CurrentUser.id(), TaskFocus.fromParam(focus), page, size);
	}
}
