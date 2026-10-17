package io.github.aryan52.tenantflow.tenant.dto;

/** Tenant-wide counts shown on the dashboard; identical for every member, so cacheable per tenant. */
public record TenantStats(
		long totalProjects,
		long totalTasks,
		long todoTasks,
		long inProgressTasks,
		long doneTasks,
		long overdueTasks,
		/** Open tasks due today through the next 7 days (overdue ones are counted separately). */
		long dueSoonTasks,
		long totalMembers
) {
}
