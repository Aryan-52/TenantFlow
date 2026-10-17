package io.github.aryan52.tenantflow.analytics;

import io.github.aryan52.tenantflow.analytics.AnalyticsResponse.Bucket;
import io.github.aryan52.tenantflow.analytics.AnalyticsResponse.Delivery;
import io.github.aryan52.tenantflow.analytics.AnalyticsResponse.HealthCriteria;
import io.github.aryan52.tenantflow.analytics.AnalyticsResponse.ProjectHealth;
import io.github.aryan52.tenantflow.analytics.AnalyticsResponse.Projects;
import io.github.aryan52.tenantflow.analytics.AnalyticsResponse.Range;
import io.github.aryan52.tenantflow.analytics.AnalyticsResponse.Signal;
import io.github.aryan52.tenantflow.analytics.AnalyticsResponse.Tasks;
import io.github.aryan52.tenantflow.analytics.AnalyticsResponse.TrendPoint;
import io.github.aryan52.tenantflow.analytics.AnalyticsResponse.Workload;
import io.github.aryan52.tenantflow.analytics.AnalyticsResponse.Flow;
import io.github.aryan52.tenantflow.analytics.AnalyticsResponse.MilestoneItem;
import io.github.aryan52.tenantflow.analytics.AnalyticsResponse.Milestones;
import io.github.aryan52.tenantflow.entity.ProjectStatus;
import io.github.aryan52.tenantflow.entity.TaskPriority;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import io.github.aryan52.tenantflow.error.ResourceNotFoundException;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workspace and project analytics computed from the live tables. Counts are aggregated in
 * the database; time series select only the timestamp columns inside the requested range.
 * Nothing is estimated: metrics that need data the application has not recorded (e.g.
 * completion times before V15) are returned as null and the response says why.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

	/** Health rules. Returned with every response so the UI can explain each signal. */
	static final int DUE_SOON_DAYS = 7;
	static final int DUE_SOON_MIN = 3;
	static final int STALE_DAYS = 14;
	static final int CONCENTRATION_MIN_OPEN = 5;
	static final double CONCENTRATION_SHARE = 0.6;
	static final int UNASSIGNED_MIN = 3;
	static final int TARGET_NEAR_DAYS = 14;
	/** "Behind schedule": share of planned time passed exceeds share of tasks done by this many points. */
	static final int SCHEDULE_GAP_PERCENT = 25;
	static final int UPCOMING_MILESTONE_DAYS = 30;
	static final int MAX_RANGE_DAYS = 366;

	private final EntityManager em;
	private final ProjectRepository projectRepository;

	@Transactional(readOnly = true)
	public AnalyticsResponse analytics(UUID tenantId, UUID projectId, LocalDate from, LocalDate to) {
		if (projectId != null) {
			projectRepository.findByIdAndTenant_Id(projectId, tenantId).orElseThrow(() -> new ResourceNotFoundException("Project"));
		}
		LocalDate today = LocalDate.now();
		LocalDate end = to == null ? today : to;
		LocalDate start = from == null ? end.minusDays(55) : from;
		if (start.isAfter(end)) {
			throw new IllegalArgumentException("'from' must not be after 'to'");
		}
		if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
			throw new IllegalArgumentException("The date range can be at most one year");
		}
		boolean weekly = ChronoUnit.DAYS.between(start, end) > 31;
		Scope scope = new Scope(tenantId, projectId);

		return new AnalyticsResponse(
				Instant.now(),
				new Range(start, end, weekly ? "WEEK" : "DAY"),
				projectId == null ? projects(scope) : null,
				tasks(scope, today),
				workload(scope, today),
				trend(scope, start, end, weekly),
				delivery(scope, start, end),
				health(scope, today),
				criteria(),
				flow(scope, today),
				milestones(scope, today, start, end)
		);
	}

	static HealthCriteria criteria() {
		return new HealthCriteria(DUE_SOON_DAYS, DUE_SOON_MIN, STALE_DAYS, CONCENTRATION_MIN_OPEN,
				(int) Math.round(CONCENTRATION_SHARE * 100), UNASSIGNED_MIN, TARGET_NEAR_DAYS, SCHEDULE_GAP_PERCENT);
	}

	/** Health of every active and on-hold project of a workspace (portfolio). */
	@Transactional(readOnly = true)
	public List<ProjectHealth> health(UUID tenantId) {
		return health(new Scope(tenantId, null), LocalDate.now());
	}

	private Flow flow(Scope scope, LocalDate today) {
		Long blocked = scope.bind(em.createQuery("select count(distinct t.id) from Task t, "
						+ "io.github.aryan52.tenantflow.planning.TaskDependency d, Task b where " + scope.where("t")
						+ " and t.status <> :done and d.blockedTaskId = t.id and b.id = d.blockingTaskId and b.status <> :done", Long.class))
				.setParameter("done", TaskStatus.DONE)
				.getSingleResult();
		ZoneId zone = ZoneId.systemDefault();
		List<Instant> created = scope.bind(em.createQuery("select t.createdAt from Task t where " + scope.where("t")
						+ " and t.status <> :done", Instant.class))
				.setParameter("done", TaskStatus.DONE)
				.getResultList();
		long[] buckets = new long[4];
		double totalDays = 0;
		for (Instant c : created) {
			long days = ChronoUnit.DAYS.between(c.atZone(zone).toLocalDate(), today);
			totalDays += days;
			buckets[days <= 7 ? 0 : days <= 30 ? 1 : days <= 90 ? 2 : 3]++;
		}
		return new Flow(blocked == null ? 0 : blocked,
				List.of(new Bucket("≤ 7 days", buckets[0]), new Bucket("8–30 days", buckets[1]),
						new Bucket("31–90 days", buckets[2]), new Bucket("> 90 days", buckets[3])),
				created.isEmpty() ? null : Math.round(totalDays / created.size() * 10) / 10.0);
	}

	private Milestones milestones(Scope scope, LocalDate today, LocalDate start, LocalDate end) {
		String where = "m.tenantId = :tenantId" + (scope.projectId() == null ? "" : " and m.projectId = :projectId");
		var q = em.createQuery("select m.id, m.projectId, p.name, m.name, m.targetDate, m.status, m.completedAt "
				+ "from io.github.aryan52.tenantflow.planning.Milestone m, Project p where p.id = m.projectId and " + where
				+ " order by m.targetDate asc", Object[].class)
				.setParameter("tenantId", scope.tenantId());
		if (scope.projectId() != null) {
			q.setParameter("projectId", scope.projectId());
		}
		ZoneId zone = ZoneId.systemDefault();
		long total = 0;
		long done = 0;
		long overdue = 0;
		long completedInRange = 0;
		List<MilestoneItem> upcoming = new ArrayList<>();
		for (Object[] r : q.getResultList()) {
			total++;
			LocalDate target = (LocalDate) r[4];
			String status = r[5].toString();
			Instant completedAt = (Instant) r[6];
			if ("DONE".equals(status)) {
				done++;
				if (completedAt != null) {
					LocalDate d = completedAt.atZone(zone).toLocalDate();
					if (!d.isBefore(start) && !d.isAfter(end)) {
						completedInRange++;
					}
				}
				continue;
			}
			if (target.isBefore(today)) {
				overdue++;
			}
			if (!target.isAfter(today.plusDays(UPCOMING_MILESTONE_DAYS)) && upcoming.size() < 8) {
				upcoming.add(new MilestoneItem((UUID) r[0], (UUID) r[1], (String) r[2], (String) r[3], target, status));
			}
		}
		return new Milestones(total, done, overdue, completedInRange, upcoming);
	}

	/** Tenant (and optionally project) restriction applied to every task query. */
	private record Scope(UUID tenantId, UUID projectId) {
		String where(String alias) {
			return alias + ".project.tenant.id = :tenantId" + (projectId == null ? "" : " and " + alias + ".project.id = :projectId");
		}

		<T> TypedQuery<T> bind(TypedQuery<T> q) {
			q.setParameter("tenantId", tenantId);
			if (projectId != null) {
				q.setParameter("projectId", projectId);
			}
			return q;
		}
	}

	private Projects projects(Scope scope) {
		Map<ProjectStatus, Long> byStatus = new EnumMap<>(ProjectStatus.class);
		for (ProjectStatus s : ProjectStatus.values()) {
			byStatus.put(s, 0L);
		}
		em.createQuery("select p.status, count(p) from Project p where p.tenant.id = :tenantId group by p.status", Object[].class)
				.setParameter("tenantId", scope.tenantId())
				.getResultList()
				.forEach(r -> byStatus.put((ProjectStatus) r[0], (Long) r[1]));

		// Progress of every project (done / total), bucketed. Projects without tasks are counted separately.
		List<Object[]> rows = em.createQuery("""
						select p.id, count(t), sum(case when t.status = :done then 1 else 0 end)
						from Project p left join Task t on t.project = p
						where p.tenant.id = :tenantId and p.status <> :archived
						group by p.id""", Object[].class)
				.setParameter("tenantId", scope.tenantId())
				.setParameter("done", TaskStatus.DONE)
				.setParameter("archived", ProjectStatus.ARCHIVED)
				.getResultList();
		long[] buckets = new long[4];
		long noTasks = 0;
		for (Object[] r : rows) {
			long total = (Long) r[1];
			long done = r[2] == null ? 0 : ((Number) r[2]).longValue();
			if (total == 0) {
				noTasks++;
				continue;
			}
			double pct = (double) done / total;
			buckets[pct >= 1.0 ? 3 : pct >= 0.5 ? 2 : pct >= 0.25 ? 1 : 0]++;
		}
		List<Bucket> progress = List.of(
				new Bucket("0–24%", buckets[0]), new Bucket("25–49%", buckets[1]),
				new Bucket("50–99%", buckets[2]), new Bucket("100%", buckets[3]), new Bucket("No tasks", noTasks));
		long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
		return new Projects(total, byStatus, progress);
	}

	private Tasks tasks(Scope scope, LocalDate today) {
		Map<TaskStatus, Long> byStatus = new EnumMap<>(TaskStatus.class);
		for (TaskStatus s : TaskStatus.values()) {
			byStatus.put(s, 0L);
		}
		scope.bind(em.createQuery("select t.status, count(t) from Task t where " + scope.where("t") + " group by t.status", Object[].class))
				.getResultList()
				.forEach(r -> byStatus.put((TaskStatus) r[0], (Long) r[1]));

		Map<TaskPriority, Long> openByPriority = new EnumMap<>(TaskPriority.class);
		for (TaskPriority p : TaskPriority.values()) {
			openByPriority.put(p, 0L);
		}
		scope.bind(em.createQuery("select t.priority, count(t) from Task t where " + scope.where("t")
						+ " and t.status <> :done group by t.priority", Object[].class))
				.setParameter("done", TaskStatus.DONE)
				.getResultList()
				.forEach(r -> openByPriority.put((TaskPriority) r[0], (Long) r[1]));

		Object[] open = scope.bind(em.createQuery("""
						select
							sum(case when t.dueDate < :today then 1 else 0 end),
							sum(case when t.dueDate >= :today and t.dueDate <= :soon then 1 else 0 end),
							sum(case when t.assignee is null then 1 else 0 end),
							sum(case when t.dueDate is null then 1 else 0 end)
						from Task t""" + " where " + scope.where("t") + " and t.status <> :done", Object[].class))
				.setParameter("today", today)
				.setParameter("soon", today.plusDays(DUE_SOON_DAYS))
				.setParameter("done", TaskStatus.DONE)
				.getSingleResult();

		long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
		long done = byStatus.get(TaskStatus.DONE);
		return new Tasks(total, byStatus, total - done, num(open[0]), num(open[1]), num(open[2]), num(open[3]),
				total == 0 ? null : (double) done / total, openByPriority);
	}

	private List<Workload> workload(Scope scope, LocalDate today) {
		List<Object[]> rows = scope.bind(em.createQuery("""
						select a.id, a.name,
							sum(case when t.status = :todo then 1 else 0 end),
							sum(case when t.status = :progress then 1 else 0 end),
							sum(case when t.dueDate < :today then 1 else 0 end)
						from Task t left join t.assignee a
						""" + " where " + scope.where("t") + " and t.status <> :done group by a.id, a.name", Object[].class))
				.setParameter("todo", TaskStatus.TODO)
				.setParameter("progress", TaskStatus.IN_PROGRESS)
				.setParameter("done", TaskStatus.DONE)
				.setParameter("today", today)
				.getResultList();
		List<Workload> result = new ArrayList<>();
		for (Object[] r : rows) {
			result.add(new Workload((UUID) r[0], (String) r[1], num(r[2]), num(r[3]), num(r[4])));
		}
		// Busiest first; unassigned (null id) last.
		result.sort(Comparator.comparing((Workload w) -> w.userId() == null).thenComparing(w -> -(w.todo() + w.inProgress())));
		return result;
	}

	private List<TrendPoint> trend(Scope scope, LocalDate start, LocalDate end, boolean weekly) {
		ZoneId zone = ZoneId.systemDefault();
		Instant from = start.atStartOfDay(zone).toInstant();
		Instant until = end.plusDays(1).atStartOfDay(zone).toInstant();

		Map<LocalDate, long[]> points = new LinkedHashMap<>();
		for (LocalDate d = bucket(start, weekly); !d.isAfter(end); d = d.plusDays(weekly ? 7 : 1)) {
			points.put(d, new long[3]);
		}
		scope.bind(em.createQuery("select t.createdAt from Task t where " + scope.where("t")
						+ " and t.createdAt >= :from and t.createdAt < :until", Instant.class))
				.setParameter("from", from).setParameter("until", until)
				.getResultList()
				.forEach(i -> add(points, bucket(i.atZone(zone).toLocalDate(), weekly), 0));
		scope.bind(em.createQuery("select t.completedAt from Task t where " + scope.where("t")
						+ " and t.completedAt >= :from and t.completedAt < :until", Instant.class))
				.setParameter("from", from).setParameter("until", until)
				.getResultList()
				.forEach(i -> add(points, bucket(i.atZone(zone).toLocalDate(), weekly), 1));
		scope.bind(em.createQuery("select t.dueDate from Task t where " + scope.where("t")
						+ " and t.dueDate >= :start and t.dueDate <= :end", LocalDate.class))
				.setParameter("start", start).setParameter("end", end)
				.getResultList()
				.forEach(d -> add(points, bucket(d, weekly), 2));

		// Overdue at the end of each bucket, from recorded dates only.
		List<Object[]> dated = scope.bind(em.createQuery("select t.createdAt, t.dueDate, t.status, t.completedAt from Task t where "
						+ scope.where("t") + " and t.dueDate is not null and t.dueDate < :end", Object[].class))
				.setParameter("end", end)
				.getResultList();
		List<TrendPoint> result = new ArrayList<>();
		points.forEach((date, v) -> {
			LocalDate bucketEnd = weekly ? date.plusDays(6) : date;
			if (bucketEnd.isAfter(end)) {
				bucketEnd = end;
			}
			Instant endOfBucket = bucketEnd.plusDays(1).atStartOfDay(zone).toInstant();
			long overdueOpen = 0;
			for (Object[] t : dated) {
				Instant createdAt = (Instant) t[0];
				LocalDate due = (LocalDate) t[1];
				boolean doneNow = t[2] == TaskStatus.DONE;
				Instant completedAt = (Instant) t[3];
				if (doneNow && completedAt == null) {
					continue; // completion time unknown
				}
				boolean openThen = !doneNow || !completedAt.isBefore(endOfBucket);
				// Overdue on bucketEnd = due before that day and still open at its end.
				if (createdAt.isBefore(endOfBucket) && due.isBefore(bucketEnd) && openThen) {
					overdueOpen++;
				}
			}
			result.add(new TrendPoint(date, v[0], v[1], v[2], overdueOpen));
		});
		return result;
	}

	private Delivery delivery(Scope scope, LocalDate start, LocalDate end) {
		ZoneId zone = ZoneId.systemDefault();
		Instant from = start.atStartOfDay(zone).toInstant();
		Instant until = end.plusDays(1).atStartOfDay(zone).toInstant();
		List<Object[]> completed = scope.bind(em.createQuery("select t.completedAt, t.dueDate, t.createdAt from Task t where "
						+ scope.where("t") + " and t.completedAt >= :from and t.completedAt < :until", Object[].class))
				.setParameter("from", from).setParameter("until", until)
				.getResultList();
		long onTime = 0;
		long late = 0;
		long noDue = 0;
		double totalHours = 0;
		for (Object[] r : completed) {
			Instant completedAt = (Instant) r[0];
			LocalDate due = (LocalDate) r[1];
			Instant created = (Instant) r[2];
			if (due == null) {
				noDue++;
			} else if (!completedAt.atZone(zone).toLocalDate().isAfter(due)) {
				onTime++;
			} else {
				late++;
			}
			totalHours += Math.max(0, Duration.between(created, completedAt).toMinutes() / 60.0);
		}
		Instant trackedSince = scope.bind(em.createQuery("select min(t.completedAt) from Task t where " + scope.where("t"), Instant.class))
				.getSingleResult();
		Long untracked = scope.bind(em.createQuery("select count(t) from Task t where " + scope.where("t")
						+ " and t.status = :done and t.completedAt is null", Long.class))
				.setParameter("done", TaskStatus.DONE)
				.getSingleResult();
		long withDue = onTime + late;
		return new Delivery(completed.size(), onTime, late, noDue,
				withDue == 0 ? null : (double) onTime / withDue,
				completed.isEmpty() ? null : Math.round(totalHours / completed.size() / 24.0 * 10) / 10.0,
				trackedSince, untracked);
	}

	private List<ProjectHealth> health(Scope scope, LocalDate today) {
		String projectFilter = scope.projectId() == null ? "" : " and p.id = :projectId";
		var q = em.createQuery("""
						select p.id, p.name, p.status, o.name, p.updatedAt, p.startDate, p.targetDate,
							count(t),
							sum(case when t.status = :done then 1 else 0 end),
							sum(case when t.status <> :done and t.dueDate < :today then 1 else 0 end),
							sum(case when t.status <> :done and t.dueDate >= :today and t.dueDate <= :soon then 1 else 0 end),
							sum(case when t.status <> :done and (t.priority = :urgent or (t.priority = :high and t.dueDate < :today)) then 1 else 0 end),
							sum(case when t.status <> :done and t.assignee is null then 1 else 0 end),
							max(t.updatedAt)
						from Project p left join p.owner o left join Task t on t.project = p
						where p.tenant.id = :tenantId and p.status in (:active, :onHold)""" + projectFilter
						+ " group by p.id, p.name, p.status, o.name, p.updatedAt, p.startDate, p.targetDate", Object[].class)
				.setParameter("tenantId", scope.tenantId())
				.setParameter("done", TaskStatus.DONE)
				.setParameter("today", today)
				.setParameter("soon", today.plusDays(DUE_SOON_DAYS))
				.setParameter("urgent", TaskPriority.URGENT)
				.setParameter("high", TaskPriority.HIGH)
				.setParameter("active", ProjectStatus.ACTIVE)
				.setParameter("onHold", ProjectStatus.ON_HOLD);
		if (scope.projectId() != null) {
			q.setParameter("projectId", scope.projectId());
		}

		// Largest share of a project's open work held by one person.
		Map<UUID, Object[]> topAssignee = new HashMap<>();
		var topQuery = em.createQuery("""
						select t.project.id, a.name, count(t)
						from Task t join t.assignee a
						where t.project.tenant.id = :tenantId and t.status <> :done
						group by t.project.id, a.id, a.name""", Object[].class)
				.setParameter("tenantId", scope.tenantId())
				.setParameter("done", TaskStatus.DONE);
		for (Object[] r : topQuery.getResultList()) {
			Object[] best = topAssignee.get((UUID) r[0]);
			if (best == null || (Long) r[2] > (Long) best[1]) {
				topAssignee.put((UUID) r[0], new Object[] {r[1], r[2]});
			}
		}

		Map<UUID, Long> blockedByProject = new HashMap<>();
		em.createQuery("""
						select t.project.id, count(distinct t.id) from Task t, io.github.aryan52.tenantflow.planning.TaskDependency d, Task b
						where t.project.tenant.id = :tenantId and t.status <> :done and d.blockedTaskId = t.id
						and b.id = d.blockingTaskId and b.status <> :done group by t.project.id""", Object[].class)
				.setParameter("tenantId", scope.tenantId())
				.setParameter("done", TaskStatus.DONE)
				.getResultList()
				.forEach(r -> blockedByProject.put((UUID) r[0], (Long) r[1]));
		Map<UUID, Long> lateMilestones = new HashMap<>();
		em.createQuery("""
						select m.projectId, count(m) from io.github.aryan52.tenantflow.planning.Milestone m
						where m.tenantId = :tenantId and m.status = io.github.aryan52.tenantflow.planning.Milestone.Status.OPEN
						and m.targetDate < :today group by m.projectId""", Object[].class)
				.setParameter("tenantId", scope.tenantId())
				.setParameter("today", today)
				.getResultList()
				.forEach(r -> lateMilestones.put((UUID) r[0], (Long) r[1]));

		List<ProjectHealth> result = new ArrayList<>();
		for (Object[] r : q.getResultList()) {
			UUID id = (UUID) r[0];
			LocalDate startDate = (LocalDate) r[5];
			LocalDate targetDate = (LocalDate) r[6];
			long total = (Long) r[7];
			long done = num(r[8]);
			long open = total - done;
			long overdue = num(r[9]);
			long dueSoon = num(r[10]);
			long urgent = num(r[11]);
			long unassigned = num(r[12]);
			Instant projectUpdated = (Instant) r[4];
			Instant taskUpdated = (Instant) r[13];
			long blocked = blockedByProject.getOrDefault(id, 0L);
			long overdueMilestones = lateMilestones.getOrDefault(id, 0L);
			Instant lastActivity = taskUpdated == null || projectUpdated.isAfter(taskUpdated) ? projectUpdated : taskUpdated;

			List<Signal> signals = new ArrayList<>();
			if (overdue > 0) {
				signals.add(new Signal("OVERDUE", "high", overdue + " overdue task" + (overdue == 1 ? "" : "s")));
			}
			if (urgent > 0) {
				signals.add(new Signal("HIGH_PRIORITY", "high", urgent + " urgent or overdue high-priority task" + (urgent == 1 ? "" : "s") + " open"));
			}
			if (dueSoon >= DUE_SOON_MIN) {
				signals.add(new Signal("DUE_SOON", "medium", dueSoon + " tasks due in the next " + DUE_SOON_DAYS + " days"));
			}
			long idleDays = ChronoUnit.DAYS.between(lastActivity.atZone(ZoneId.systemDefault()).toLocalDate(), today);
			if (open > 0 && idleDays >= STALE_DAYS) {
				signals.add(new Signal("STALE", "medium", "No updates in " + idleDays + " days with " + open + " open task" + (open == 1 ? "" : "s")));
			}
			Object[] top = topAssignee.get(id);
			if (top != null && open >= CONCENTRATION_MIN_OPEN && (Long) top[1] >= Math.ceil(open * CONCENTRATION_SHARE)) {
				signals.add(new Signal("CONCENTRATION", "medium", top[0] + " holds " + top[1] + " of " + open + " open tasks"));
			}
			if (unassigned >= UNASSIGNED_MIN) {
				signals.add(new Signal("UNASSIGNED", "low", unassigned + " open tasks have no assignee"));
			}
			if (overdueMilestones > 0) {
				signals.add(new Signal("MILESTONE_OVERDUE", "high", overdueMilestones + " milestone" + (overdueMilestones == 1 ? " is" : "s are") + " past the target date"));
			}
			if (blocked > 0) {
				signals.add(new Signal("BLOCKED", "medium", blocked + " open task" + (blocked == 1 ? " is" : "s are") + " blocked by unfinished work"));
			}
			if (targetDate != null && open > 0) {
				long daysLeft = ChronoUnit.DAYS.between(today, targetDate);
				if (daysLeft < 0) {
					signals.add(new Signal("TARGET_PASSED", "high", "Target date passed " + (-daysLeft) + " day" + (daysLeft == -1 ? "" : "s") + " ago with " + open + " open task" + (open == 1 ? "" : "s")));
				} else if (daysLeft <= TARGET_NEAR_DAYS) {
					signals.add(new Signal("TARGET_NEAR", "medium", "Target date in " + daysLeft + " day" + (daysLeft == 1 ? "" : "s") + " with " + open + " of " + total + " tasks open"));
				}
				if (startDate != null && !today.isBefore(startDate) && daysLeft >= 0 && total > 0) {
					long span = Math.max(1, ChronoUnit.DAYS.between(startDate, targetDate));
					int timePct = (int) Math.min(100, Math.round(ChronoUnit.DAYS.between(startDate, today) * 100.0 / span));
					int donePct = (int) Math.round(done * 100.0 / total);
					if (timePct - donePct >= SCHEDULE_GAP_PERCENT) {
						signals.add(new Signal("BEHIND_SCHEDULE", "medium", timePct + "% of the planned time has passed; " + donePct + "% of tasks are done"));
					}
				}
			}
			result.add(new ProjectHealth(id, (String) r[1], (ProjectStatus) r[2], (String) r[3], total, done, open, overdue,
					dueSoon, urgent, unassigned, lastActivity, signals, startDate, targetDate, blocked, overdueMilestones));
		}
		// Most signals (weighted by severity) first.
		result.sort(Comparator.comparingInt((ProjectHealth h) -> -h.signals().stream()
						.mapToInt(s -> "high".equals(s.severity()) ? 3 : "medium".equals(s.severity()) ? 2 : 1).sum())
				.thenComparing(h -> -h.overdue()));
		return result;
	}

	private static LocalDate bucket(LocalDate d, boolean weekly) {
		return weekly ? d.minusDays(d.getDayOfWeek().getValue() - 1L) : d;
	}

	private static void add(Map<LocalDate, long[]> points, LocalDate key, int index) {
		long[] v = points.get(key);
		if (v != null) {
			v[index]++;
		}
	}

	private static long num(Object o) {
		return o == null ? 0 : ((Number) o).longValue();
	}
}
