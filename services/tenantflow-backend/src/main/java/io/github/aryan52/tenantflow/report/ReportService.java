package io.github.aryan52.tenantflow.report;

import io.github.aryan52.tenantflow.analytics.AnalyticsResponse.Signal;
import io.github.aryan52.tenantflow.entity.Task;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import io.github.aryan52.tenantflow.error.ResourceNotFoundException;
import io.github.aryan52.tenantflow.planning.DependencyService;
import io.github.aryan52.tenantflow.planning.MilestoneService;
import io.github.aryan52.tenantflow.planning.PlanningService;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import io.github.aryan52.tenantflow.repository.TaskRepository;
import io.github.aryan52.tenantflow.report.Report.Column;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workspace reports built from live data and the caller's filters. Every query is scoped to
 * the workspace in the URL (which the controller has checked membership of); filter ids
 * from another workspace simply match nothing.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

	public enum Type {
		PROJECT_STATUS("project-status", "Project status summary", "Every project with progress, dates, open and overdue work and health."),
		OVERDUE_TASKS("overdue-tasks", "Overdue tasks", "Open tasks past their due date, most overdue first."),
		WORKLOAD("workload", "Workload distribution", "Open tasks per member, with capacity where it is set."),
		TASK_COMPLETION("task-completion", "Task completion", "Tasks completed in the date range, with on-time status."),
		MILESTONES("milestones", "Milestone status", "Every milestone with its target date, status and task progress.");

		final String slug;
		final String title;
		final String description;

		Type(String slug, String title, String description) {
			this.slug = slug;
			this.title = title;
			this.description = description;
		}

		public String slug() {
			return slug;
		}

		public static Type fromSlug(String slug) {
			for (Type t : values()) {
				if (t.slug.equals(slug)) {
					return t;
				}
			}
			throw new IllegalArgumentException("Unknown report. Choose one of: project-status, overdue-tasks, workload, task-completion, milestones");
		}
	}

	public record Filters(UUID projectId, UUID assigneeId, LocalDate from, LocalDate to) {
	}

	private final PlanningService planningService;
	private final MilestoneService milestoneService;
	private final DependencyService dependencyService;
	private final TaskRepository taskRepository;
	private final ProjectRepository projectRepository;
	private final MembershipRepository membershipRepository;

	@Transactional(readOnly = true)
	public Report build(UUID tenantId, UUID userId, Type type, Filters f) {
		if (f.projectId() != null) {
			projectRepository.findByIdAndTenant_Id(f.projectId(), tenantId).orElseThrow(() -> new ResourceNotFoundException("Project"));
		}
		if (f.assigneeId() != null && !membershipRepository.existsByUser_IdAndTenant_Id(f.assigneeId(), tenantId)) {
			throw new ResourceNotFoundException("Member");
		}
		LocalDate today = LocalDate.now();
		Map<String, String> applied = new LinkedHashMap<>();
		if (f.projectId() != null) {
			applied.put("projectId", f.projectId().toString());
		}
		if (f.assigneeId() != null) {
			applied.put("assigneeId", f.assigneeId().toString());
		}
		return switch (type) {
			case PROJECT_STATUS -> projectStatus(tenantId, f, applied);
			case OVERDUE_TASKS -> overdue(tenantId, f, today, applied);
			case WORKLOAD -> workload(tenantId, userId, f, applied);
			case TASK_COMPLETION -> completion(tenantId, f, today, applied);
			case MILESTONES -> milestones(tenantId, f, today, applied);
		};
	}

	private Report projectStatus(UUID tenantId, Filters f, Map<String, String> applied) {
		List<Column> cols = List.of(Column.text("project", "Project"), Column.text("status", "Status"), Column.text("owner", "Owner"),
				Column.text("startDate", "Start date"), Column.text("targetDate", "Target date"), Column.number("tasks", "Tasks"),
				Column.number("done", "Done"), Column.number("progressPercent", "Progress %"), Column.number("open", "Open"),
				Column.number("overdue", "Overdue"), Column.number("blocked", "Blocked"), Column.text("nextMilestone", "Next milestone"),
				Column.text("attention", "Needs attention"));
		List<List<String>> rows = planningService.portfolio(tenantId).stream()
				.filter(p -> f.projectId() == null || p.id().equals(f.projectId()))
				.filter(p -> f.assigneeId() == null || f.assigneeId().equals(p.ownerId()))
				.map(p -> List.of(p.name(), label(p.status().name()), s(p.ownerName()), s(p.startDate()), s(p.targetDate()),
						n(p.totalTasks()), n(p.doneTasks()), p.totalTasks() == 0 ? "" : n(Math.round(p.doneTasks() * 100.0 / p.totalTasks())),
						n(p.openTasks()), n(p.overdueTasks()), n(p.blockedTasks()),
						p.nextMilestone() == null ? "" : p.nextMilestone().name() + " (" + p.nextMilestone().targetDate() + ")",
						p.signals().stream().map(Signal::message).collect(Collectors.joining("; "))))
				.toList();
		if (f.assigneeId() != null) {
			applied.put("assigneeId (project owner)", applied.remove("assigneeId"));
		}
		return report(Type.PROJECT_STATUS, cols, rows, applied);
	}

	private Report overdue(UUID tenantId, Filters f, LocalDate today, Map<String, String> applied) {
		List<Task> tasks = taskRepository.findByProject_Tenant_Id(tenantId).stream()
				.filter(t -> t.getStatus() != TaskStatus.DONE && t.getDueDate() != null && t.getDueDate().isBefore(today))
				.filter(t -> matches(t, f))
				.sorted(Comparator.comparing(Task::getDueDate).thenComparing(Task::getTitle))
				.toList();
		Map<UUID, Integer> blockers = dependencyService.openBlockerCounts(tasks.stream().map(Task::getId).toList());
		List<Column> cols = List.of(Column.text("task", "Task"), Column.text("project", "Project"), Column.text("assignee", "Assignee"),
				Column.text("priority", "Priority"), Column.text("status", "Status"), Column.text("dueDate", "Due date"),
				Column.number("daysOverdue", "Days overdue"), Column.number("openBlockers", "Blocked by"));
		List<List<String>> rows = tasks.stream().map(t -> List.of(t.getTitle(), t.getProject().getName(),
				t.getAssignee() == null ? "Unassigned" : t.getAssignee().getName(), label(t.getPriority().name()),
				label(t.getStatus().name()), s(t.getDueDate()), n(ChronoUnit.DAYS.between(t.getDueDate(), today)),
				n(blockers.getOrDefault(t.getId(), 0)))).toList();
		return report(Type.OVERDUE_TASKS, cols, rows, applied);
	}

	private Report workload(UUID tenantId, UUID userId, Filters f, Map<String, String> applied) {
		var w = planningService.workload(tenantId, userId, 1);
		List<Column> cols = List.of(Column.text("member", "Member"), Column.text("role", "Role"), Column.number("open", "Open tasks"),
				Column.number("todo", "To do"), Column.number("inProgress", "In progress"), Column.number("overdue", "Overdue"),
				Column.number("blocked", "Blocked"), Column.number("dueThisWeek", "Due this week"), Column.number("noDueDate", "No due date"),
				Column.number("weeklyCapacity", "Capacity (tasks/week)"));
		List<List<String>> rows = new ArrayList<>();
		w.members().stream().filter(m -> f.assigneeId() == null || f.assigneeId().equals(m.userId())).forEach(m -> rows.add(List.of(
				m.name(), label(m.role().name()), n(m.open()), n(m.todo()), n(m.inProgress()), n(m.overdue()), n(m.blocked()),
				n(m.weeks().isEmpty() ? 0 : m.weeks().get(0).due()), n(m.noDueDate()), m.weeklyCapacity() == null ? "" : n(m.weeklyCapacity()))));
		if (f.assigneeId() == null && w.unassigned().open() > 0) {
			var u = w.unassigned();
			rows.add(List.of("Unassigned", "", n(u.open()), n(u.todo()), n(u.inProgress()), n(u.overdue()), n(u.blocked()),
					n(u.weeks().isEmpty() ? 0 : u.weeks().get(0).due()), n(u.noDueDate()), ""));
		}
		if (f.projectId() != null) {
			applied.remove("projectId"); // workload is workspace-wide
		}
		return report(Type.WORKLOAD, cols, rows, applied);
	}

	private Report completion(UUID tenantId, Filters f, LocalDate today, Map<String, String> applied) {
		LocalDate to = f.to() == null ? today : f.to();
		LocalDate from = f.from() == null ? to.minusDays(29) : f.from();
		if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > 366) {
			throw new IllegalArgumentException("Choose a date range of at most one year");
		}
		applied.put("from", from.toString());
		applied.put("to", to.toString());
		ZoneId zone = ZoneId.systemDefault();
		Instant start = from.atStartOfDay(zone).toInstant();
		Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
		List<Task> tasks = taskRepository.findByProject_Tenant_Id(tenantId).stream()
				.filter(t -> t.getStatus() == TaskStatus.DONE && t.getCompletedAt() != null
						&& !t.getCompletedAt().isBefore(start) && t.getCompletedAt().isBefore(end))
				.filter(t -> matches(t, f))
				.sorted(Comparator.comparing(Task::getCompletedAt).reversed())
				.toList();
		List<Column> cols = List.of(Column.text("task", "Task"), Column.text("project", "Project"), Column.text("assignee", "Assignee"),
				Column.text("completedOn", "Completed on"), Column.text("dueDate", "Due date"), Column.text("onTime", "On time"),
				Column.number("daysToComplete", "Days from creation"));
		List<List<String>> rows = tasks.stream().map(t -> {
			LocalDate done = t.getCompletedAt().atZone(zone).toLocalDate();
			String onTime = t.getDueDate() == null ? "No due date" : done.isAfter(t.getDueDate()) ? "Late" : "On time";
			return List.of(t.getTitle(), t.getProject().getName(), t.getAssignee() == null ? "Unassigned" : t.getAssignee().getName(),
					done.toString(), s(t.getDueDate()), onTime,
					n(ChronoUnit.DAYS.between(t.getCreatedAt().atZone(zone).toLocalDate(), done)));
		}).toList();
		return report(Type.TASK_COMPLETION, cols, rows, applied);
	}

	private Report milestones(UUID tenantId, Filters f, LocalDate today, Map<String, String> applied) {
		List<Column> cols = List.of(Column.text("milestone", "Milestone"), Column.text("project", "Project"),
				Column.text("targetDate", "Target date"), Column.text("status", "Status"), Column.text("completedOn", "Completed on"),
				Column.number("tasks", "Tasks"), Column.number("tasksDone", "Tasks done"));
		ZoneId zone = ZoneId.systemDefault();
		List<List<String>> rows = milestoneService.listAllForTenant(tenantId).stream()
				.filter(m -> f.projectId() == null || m.projectId().equals(f.projectId()))
				.map(m -> List.of(m.name(), s(m.projectName()), s(m.targetDate()),
						m.status().name().equals("DONE") ? "Done" : m.targetDate().isBefore(today) ? "Overdue" : "Open",
						m.completedAt() == null ? "" : m.completedAt().atZone(zone).toLocalDate().toString(),
						n(m.totalTasks()), n(m.doneTasks())))
				.toList();
		if (f.assigneeId() != null) {
			applied.remove("assigneeId"); // milestones have no assignee
		}
		return report(Type.MILESTONES, cols, rows, applied);
	}

	private static boolean matches(Task t, Filters f) {
		return (f.projectId() == null || t.getProject().getId().equals(f.projectId()))
				&& (f.assigneeId() == null || (t.getAssignee() != null && t.getAssignee().getId().equals(f.assigneeId())));
	}

	private static Report report(Type type, List<Column> cols, List<List<String>> rows, Map<String, String> applied) {
		return new Report(type.slug, type.title, type.description, cols, rows, applied, Instant.now());
	}

	private static String s(Object o) {
		return o == null ? "" : o.toString();
	}

	private static String n(long v) {
		return Long.toString(v);
	}

	private static String label(String enumName) {
		String x = enumName.replace('_', ' ').toLowerCase();
		return Character.toUpperCase(x.charAt(0)) + x.substring(1);
	}
}
