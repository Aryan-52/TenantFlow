package io.github.aryan52.tenantflow.tenant.dto;

import io.github.aryan52.tenantflow.task.dto.TaskResponse;
import java.util.List;

/**
 * Dashboard for one workspace. The counts up to {@code dueSoonTasks} are tenant-wide (served
 * from the per-tenant cache); everything after them is specific to the current user and is
 * computed per request.
 */
public record DashboardResponse(
		long totalProjects,
		long totalTasks,
		long todoTasks,
		long inProgressTasks,
		long doneTasks,
		long totalMembers,
		long overdueTasks,
		long dueSoonTasks,
		String currentUserRole,
		long myOpenTasks,
		/** The current user's open tasks, soonest due first (at most {@code LIST_SIZE}). */
		List<TaskResponse> myTasks,
		/** Open tasks that are overdue or due within the "due soon" window, soonest first. */
		List<TaskResponse> upcomingTasks,
		/** Most recently updated projects with their progress. */
		List<ProjectProgress> recentProjects
) {
	public static final int LIST_SIZE = 5;
}
