package io.github.aryan52.tenantflow.analytics;

import io.github.aryan52.tenantflow.entity.ProjectStatus;
import io.github.aryan52.tenantflow.entity.TaskPriority;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Workspace (projectId absent) or project analytics. See AnalyticsService for definitions. */
public record AnalyticsResponse(
		Instant generatedAt,
		Range range,
		/** Workspace view only (null for a single project). */
		Projects projects,
		Tasks tasks,
		/** Open work per assignee; an entry with a null userId is unassigned work. */
		List<Workload> workload,
		/** Per day or week in the range: tasks created, completed and due. */
		List<TrendPoint> trend,
		Delivery delivery,
		/** Active and on-hold projects with any signals that need attention. */
		List<ProjectHealth> health,
		HealthCriteria criteria,
		Flow flow,
		Milestones milestones
) {
	public record Range(LocalDate from, LocalDate to, String bucket) {}

	public record Bucket(String label, long count) {}

	public record Projects(long total, Map<ProjectStatus, Long> byStatus, List<Bucket> progress) {}

	public record Tasks(long total, Map<TaskStatus, Long> byStatus, long open, long overdue, long dueSoon,
			long unassignedOpen, long openWithoutDueDate, Double completionRate, Map<TaskPriority, Long> openByPriority) {}

	public record Workload(UUID userId, String name, long todo, long inProgress, long overdue) {}

	/**
	 * overdueOpen: tasks open and past due at the end of the bucket, reconstructed only from
	 * recorded dates (done tasks without a recorded completion time are left out).
	 */
	public record TrendPoint(LocalDate date, long created, long completed, long due, long overdueOpen) {}

	/** Open work right now: blocked by unfinished tasks, and age since creation. */
	public record Flow(long blockedOpen, List<Bucket> openAge, Double averageOpenAgeDays) {}

	public record MilestoneItem(UUID id, UUID projectId, String projectName, String name, LocalDate targetDate,
			String status) {}

	/** Milestone status now; upcoming = open, due within the next 30 days or overdue. */
	public record Milestones(long total, long done, long overdue, long completedInRange, List<MilestoneItem> upcoming) {}

	/**
	 * Completions inside the range. onTimeRate counts only completions that had a due date;
	 * averageDaysToComplete is from creation to completion. Both are null without data.
	 * completionTrackedSince is the earliest recorded completion time; untrackedDone counts
	 * done tasks whose completion time was never recorded (completed before V15).
	 */
	public record Delivery(long completed, long onTime, long late, long withoutDueDate, Double onTimeRate,
			Double averageDaysToComplete, Instant completionTrackedSince, long untrackedDone) {}

	public record Signal(String code, String severity, String message) {}

	public record ProjectHealth(UUID projectId, String name, ProjectStatus status, String ownerName, long totalTasks,
			long doneTasks, long openTasks, long overdue, long dueSoon, long highPriorityOpen, long unassignedOpen,
			Instant lastActivityAt, List<Signal> signals, LocalDate startDate, LocalDate targetDate, long blockedOpen,
			long overdueMilestones) {}

	/** The thresholds behind the health signals, so the UI can explain them. */
	public record HealthCriteria(int dueSoonDays, int dueSoonMin, int staleDays, int concentrationMinOpen,
			int concentrationSharePercent, int unassignedMin, int targetNearDays, int scheduleGapPercent) {}
}
