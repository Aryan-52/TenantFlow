package io.github.aryan52.tenantflow.tenant.dto;

public record DashboardResponse(
		long totalProjects,
		long totalTasks,
		long todoTasks,
		long inProgressTasks,
		long doneTasks,
		long totalMembers,
		String currentUserRole
) {}
