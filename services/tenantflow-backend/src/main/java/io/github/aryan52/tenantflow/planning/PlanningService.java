package io.github.aryan52.tenantflow.planning;

import io.github.aryan52.tenantflow.analytics.AnalyticsResponse.ProjectHealth;
import io.github.aryan52.tenantflow.analytics.AnalyticsResponse.Signal;
import io.github.aryan52.tenantflow.analytics.AnalyticsService;
import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.Project;
import io.github.aryan52.tenantflow.entity.ProjectStatus;
import io.github.aryan52.tenantflow.entity.Role;
import io.github.aryan52.tenantflow.entity.Task;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import io.github.aryan52.tenantflow.error.ForbiddenOperationException;
import io.github.aryan52.tenantflow.error.ResourceNotFoundException;
import io.github.aryan52.tenantflow.planning.MilestoneService.MilestoneResponse;
import io.github.aryan52.tenantflow.preferences.PreferenceService;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import io.github.aryan52.tenantflow.repository.TaskRepository;
import io.github.aryan52.tenantflow.task.TaskService;
import io.github.aryan52.tenantflow.task.dto.TaskResponse;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cross-project planning views of one workspace: calendar, portfolio, team workload and a
 * member's overview. Everything is read from live data and scoped to the workspace; task
 * counts describe where work sits, never anyone's productivity.
 */
@Service
@RequiredArgsConstructor
public class PlanningService {

	public static final int MAX_CALENDAR_DAYS = 93;
	public static final int CALENDAR_LIMIT = 1000;

	public enum CalendarScope { MINE, WORKSPACE }

	public record Calendar(LocalDate from, LocalDate to, String scope, List<TaskResponse> tasks,
			List<MilestoneResponse> milestones, boolean truncated) {
	}

	public record NextMilestone(UUID id, String name, LocalDate targetDate) {
	}

	public record PortfolioRow(UUID id, String name, String description, ProjectStatus status, UUID ownerId,
			String ownerName, LocalDate startDate, LocalDate targetDate, long totalTasks, long doneTasks, long openTasks,
			long overdueTasks, long blockedTasks, long milestonesOpen, long milestonesDone, NextMilestone nextMilestone,
			Instant lastActivityAt, List<Signal> signals) {
	}

	public record Portfolio(List<PortfolioRow> projects, Object criteria) {
	}

	public record WeekBucket(LocalDate weekStart, long due) {
	}

	public record ProjectCount(UUID projectId, String projectName, long open) {
	}

	/** Open tasks of one member; capacity is tasks per week (null = not set). */
	public record MemberLoad(UUID userId, String name, String email, Role role, Integer weeklyCapacity, long open,
			long todo, long inProgress, long overdue, long blocked, long noDueDate, List<WeekBucket> weeks,
			long later, List<ProjectCount> projects, String warning) {
	}

	public record Workload(LocalDate today, int weeks, List<MemberLoad> members, MemberLoad unassigned) {
	}

	public record MemberOverview(UUID userId, String name, String email, Role role, Instant joinedAt,
			Integer weeklyCapacity, List<TaskResponse> open, List<TaskResponse> completedRecently,
			List<ProjectCount> projects) {
	}

	private final TaskRepository taskRepository;
	private final ProjectRepository projectRepository;
	private final MembershipRepository membershipRepository;
	private final MilestoneService milestoneService;
	private final MilestoneRepository milestoneRepository;
	private final AnalyticsService analyticsService;
	private final TaskService taskService;
	private final DependencyService dependencyService;
	private final PreferenceService preferenceService;

	// ---- Calendar -------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public Calendar calendar(UUID tenantId, UUID userId, LocalDate from, LocalDate to, CalendarScope scope, UUID projectId) {
		if (from == null || to == null || to.isBefore(from)) {
			throw new IllegalArgumentException("Choose a valid date range");
		}
		if (ChronoUnit.DAYS.between(from, to) > MAX_CALENDAR_DAYS) {
			throw new IllegalArgumentException("The calendar shows at most " + MAX_CALENDAR_DAYS + " days at a time");
		}
		if (projectId != null) {
			projectRepository.findByIdAndTenant_Id(projectId, tenantId).orElseThrow(() -> new ResourceNotFoundException("Project"));
		}
		List<Task> tasks = taskRepository.findScheduled(tenantId, from, to, PageRequest.of(0, CALENDAR_LIMIT + 1)).stream()
				.filter(t -> projectId == null || t.getProject().getId().equals(projectId))
				.filter(t -> scope != CalendarScope.MINE || (t.getAssignee() != null && t.getAssignee().getId().equals(userId)))
				.collect(Collectors.toList());
		boolean truncated = tasks.size() > CALENDAR_LIMIT;
		if (truncated) {
			tasks = tasks.subList(0, CALENDAR_LIMIT);
		}
		// Milestones are project-level: "my schedule" shows those of projects I have work in.
		var myProjects = tasks.stream().map(t -> t.getProject().getId()).collect(Collectors.toSet());
		List<MilestoneResponse> milestones = milestoneService.listForTenant(tenantId, from, to).stream()
				.filter(m -> projectId == null || m.projectId().equals(projectId))
				.filter(m -> scope != CalendarScope.MINE || myProjects.contains(m.projectId()))
				.toList();
		return new Calendar(from, to, scope.name(), taskService.withBlockers(tasks), milestones, truncated);
	}

	// ---- Portfolio -------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<PortfolioRow> portfolio(UUID tenantId) {
		LocalDate today = LocalDate.now();
		List<Project> projects = projectRepository.findByTenant_Id(tenantId);
		Map<UUID, ProjectHealth> health = analyticsService.health(tenantId).stream()
				.collect(Collectors.toMap(ProjectHealth::projectId, h -> h));

		Map<UUID, long[]> counts = new HashMap<>(); // total, done, overdue
		List<Task> tasks = taskRepository.findByProject_Tenant_Id(tenantId);
		Map<UUID, Instant> lastTaskUpdate = new HashMap<>();
		for (Task t : tasks) {
			long[] c = counts.computeIfAbsent(t.getProject().getId(), k -> new long[3]);
			c[0]++;
			if (t.getStatus() == TaskStatus.DONE) {
				c[1]++;
			} else if (t.getDueDate() != null && t.getDueDate().isBefore(today)) {
				c[2]++;
			}
			lastTaskUpdate.merge(t.getProject().getId(), t.getUpdatedAt(), (a, b) -> a.isAfter(b) ? a : b);
		}
		Map<UUID, Integer> blockers = dependencyService.openBlockerCounts(
				tasks.stream().filter(t -> t.getStatus() != TaskStatus.DONE).map(Task::getId).toList());
		Map<UUID, Long> blockedByProject = new HashMap<>();
		for (Task t : tasks) {
			if (blockers.getOrDefault(t.getId(), 0) > 0) {
				blockedByProject.merge(t.getProject().getId(), 1L, Long::sum);
			}
		}

		Map<UUID, List<Milestone>> milestones = milestoneRepository.findByTenantIdOrderByTargetDateAsc(tenantId).stream()
				.collect(Collectors.groupingBy(Milestone::getProjectId));

		List<PortfolioRow> rows = new ArrayList<>();
		for (Project p : projects) {
			long[] c = counts.getOrDefault(p.getId(), new long[3]);
			List<Milestone> ms = milestones.getOrDefault(p.getId(), List.of());
			Milestone next = ms.stream().filter(m -> m.getStatus() == Milestone.Status.OPEN).findFirst().orElse(null);
			ProjectHealth h = health.get(p.getId());
			Instant taskUpdated = lastTaskUpdate.get(p.getId());
			Instant last = taskUpdated == null || p.getUpdatedAt().isAfter(taskUpdated) ? p.getUpdatedAt() : taskUpdated;
			rows.add(new PortfolioRow(p.getId(), p.getName(), p.getDescription(), p.getStatus(),
					p.getOwner() == null ? null : p.getOwner().getId(), p.getOwner() == null ? null : p.getOwner().getName(),
					p.getStartDate(), p.getTargetDate(), c[0], c[1], c[0] - c[1], c[2],
					blockedByProject.getOrDefault(p.getId(), 0L),
					ms.stream().filter(m -> m.getStatus() == Milestone.Status.OPEN).count(),
					ms.stream().filter(m -> m.getStatus() == Milestone.Status.DONE).count(),
					next == null ? null : new NextMilestone(next.getId(), next.getName(), next.getTargetDate()),
					last, h == null ? List.of() : h.signals()));
		}
		rows.sort(Comparator.comparing(PortfolioRow::name, String.CASE_INSENSITIVE_ORDER));
		return rows;
	}

	// ---- Team workload ---------------------------------------------------------------------

	@Transactional(readOnly = true)
	public Workload workload(UUID tenantId, UUID userId, int weeks) {
		int w = Math.max(1, Math.min(12, weeks));
		LocalDate today = preferenceService.today(userId);
		LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

		List<Task> open = taskRepository.findByProject_Tenant_Id(tenantId).stream()
				.filter(t -> t.getStatus() != TaskStatus.DONE).toList();
		Map<UUID, Integer> blockers = dependencyService.openBlockerCounts(open.stream().map(Task::getId).toList());
		Map<UUID, List<Task>> byAssignee = new LinkedHashMap<>();
		List<Task> unassigned = new ArrayList<>();
		for (Task t : open) {
			if (t.getAssignee() == null) {
				unassigned.add(t);
			} else {
				byAssignee.computeIfAbsent(t.getAssignee().getId(), k -> new ArrayList<>()).add(t);
			}
		}
		List<MemberLoad> members = new ArrayList<>();
		for (Membership m : membershipRepository.findByTenant_Id(tenantId)) {
			members.add(load(m.getUser().getId(), m.getUser().getName(), m.getUser().getEmail(), m.getRole(),
					m.getWeeklyCapacity(), byAssignee.getOrDefault(m.getUser().getId(), List.of()), blockers, today, weekStart, w));
		}
		members.sort(Comparator.comparingLong(MemberLoad::open).reversed().thenComparing(MemberLoad::name, String.CASE_INSENSITIVE_ORDER));
		return new Workload(today, w, members,
				load(null, null, null, null, null, unassigned, blockers, today, weekStart, w));
	}

	private static MemberLoad load(UUID userId, String name, String email, Role role, Integer capacity, List<Task> tasks,
			Map<UUID, Integer> blockers, LocalDate today, LocalDate weekStart, int weeks) {
		long todo = 0;
		long inProgress = 0;
		long overdue = 0;
		long blocked = 0;
		long noDue = 0;
		long later = 0;
		long[] perWeek = new long[weeks];
		Map<UUID, long[]> perProject = new LinkedHashMap<>();
		Map<UUID, String> projectNames = new HashMap<>();
		for (Task t : tasks) {
			if (t.getStatus() == TaskStatus.TODO) {
				todo++;
			} else {
				inProgress++;
			}
			if (blockers.getOrDefault(t.getId(), 0) > 0) {
				blocked++;
			}
			LocalDate due = t.getDueDate();
			if (due == null) {
				noDue++;
			} else if (due.isBefore(today)) {
				overdue++;
			} else {
				long week = ChronoUnit.WEEKS.between(weekStart, due);
				if (week < weeks) {
					perWeek[(int) week]++;
				} else {
					later++;
				}
			}
			perProject.computeIfAbsent(t.getProject().getId(), k -> new long[1])[0]++;
			projectNames.put(t.getProject().getId(), t.getProject().getName());
		}
		List<WeekBucket> weekBuckets = new ArrayList<>();
		for (int i = 0; i < weeks; i++) {
			weekBuckets.add(new WeekBucket(weekStart.plusWeeks(i), perWeek[i]));
		}
		List<ProjectCount> projects = perProject.entrySet().stream()
				.map(e -> new ProjectCount(e.getKey(), projectNames.get(e.getKey()), e.getValue()[0]))
				.sorted(Comparator.comparingLong(ProjectCount::open).reversed()).toList();
		String warning = null;
		if (capacity != null) {
			// Same unit on both sides: tasks due this week (plus overdue) against tasks per week.
			long thisWeek = (weeks > 0 ? perWeek[0] : 0) + overdue;
			if (thisWeek > capacity) {
				warning = thisWeek + " tasks due this week or overdue, above the capacity of " + capacity + " per week";
			}
		} else if (overdue >= 3) {
			warning = overdue + " overdue tasks";
		}
		return new MemberLoad(userId, name, email, role, capacity, tasks.size(), todo, inProgress, overdue, blocked, noDue,
				weekBuckets, later, projects, warning);
	}

	@Transactional
	public Workload setCapacity(UUID tenantId, UUID actorId, UUID memberId, Integer capacity, boolean actorIsAdmin) {
		if (!actorIsAdmin && !Objects.equals(actorId, memberId)) {
			throw new ForbiddenOperationException("Only workspace admins can set another member's capacity");
		}
		if (capacity != null && (capacity < 0 || capacity > 200)) {
			throw new IllegalArgumentException("Capacity must be between 0 and 200 tasks per week");
		}
		Membership m = membershipRepository.findByUser_IdAndTenant_Id(memberId, tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Member"));
		m.setWeeklyCapacity(capacity);
		membershipRepository.save(m);
		return workload(tenantId, actorId, 6);
	}

	// ---- Member overview -------------------------------------------------------------------

	@Transactional(readOnly = true)
	public MemberOverview member(UUID tenantId, UUID memberId) {
		Membership m = membershipRepository.findByUser_IdAndTenant_Id(memberId, tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Member"));
		List<Task> open = taskRepository.findTop500ByProject_Tenant_IdAndAssignee_IdAndStatusNotOrderByDueDateAscCreatedAtAsc(
				tenantId, memberId, TaskStatus.DONE);
		List<Task> done = taskRepository.findTop50ByProject_Tenant_IdAndAssignee_IdAndStatusAndCompletedAtGreaterThanEqualOrderByCompletedAtDesc(
				tenantId, memberId, TaskStatus.DONE, Instant.now().minus(30, ChronoUnit.DAYS));
		Map<UUID, long[]> perProject = new LinkedHashMap<>();
		Map<UUID, String> names = new HashMap<>();
		for (Task t : open) {
			perProject.computeIfAbsent(t.getProject().getId(), k -> new long[1])[0]++;
			names.put(t.getProject().getId(), t.getProject().getName());
		}
		return new MemberOverview(memberId, m.getUser().getName(), m.getUser().getEmail(), m.getRole(), m.getCreatedAt(),
				m.getWeeklyCapacity(), taskService.withBlockers(open), done.stream().map(TaskResponse::from).toList(),
				perProject.entrySet().stream().map(e -> new ProjectCount(e.getKey(), names.get(e.getKey()), e.getValue()[0])).toList());
	}
}
