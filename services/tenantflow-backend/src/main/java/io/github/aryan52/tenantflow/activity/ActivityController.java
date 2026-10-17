package io.github.aryan52.tenantflow.activity;

import io.github.aryan52.tenantflow.activity.dto.ActivityResponse;
import io.github.aryan52.tenantflow.common.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/{tenantId}/activity")
@RequiredArgsConstructor
public class ActivityController {

	private final ActivityService activityService;

	/** Workspace activity, optionally narrowed to one project or one task. Members only. */
	@GetMapping
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public PageResponse<ActivityResponse> list(
			@PathVariable UUID tenantId,
			@RequestParam(required = false) UUID projectId,
			@RequestParam(required = false) UUID taskId,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size
	) {
		return activityService.list(tenantId, projectId, taskId, page, size);
	}
}
