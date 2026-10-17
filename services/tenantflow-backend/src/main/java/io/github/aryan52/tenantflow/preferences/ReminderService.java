package io.github.aryan52.tenantflow.preferences;

import io.github.aryan52.tenantflow.automation.AutomationService;
import io.github.aryan52.tenantflow.entity.Task;
import io.github.aryan52.tenantflow.notification.Notifier;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.TaskRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Due-date reminders (in-app only) and the scheduled "overdue" automation.
 *
 * <p>Idempotent by construction: every reminder has a dedupe key made of the task and its due
 * date ("reminder:due:{task}:{date}" / "reminder:overdue:{task}:{date}"), and notifications
 * are unique per recipient and key. Running the job again, after a restart or on several
 * instances, creates nothing new; moving a due date produces at most one new reminder.
 * "Today" is evaluated in each assignee's own time zone, and their preferences decide
 * whether and how early they are reminded.
 */
@Service
public class ReminderService {

	private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
	/** Look this far ahead (covers the largest "days before" preference plus time-zone slack). */
	private static final int HORIZON_DAYS = 16;
	private static final int BATCH = 5000;

	public record RunResult(int dueSoon, int overdue, int automationChecked) {
	}

	private final TaskRepository taskRepository;
	private final MembershipRepository membershipRepository;
	private final PreferenceService preferenceService;
	private final Notifier notifier;
	private final AutomationService automationService;
	private final TransactionTemplate tx;
	private final boolean enabled;

	public ReminderService(TaskRepository taskRepository, MembershipRepository membershipRepository,
			PreferenceService preferenceService, Notifier notifier, AutomationService automationService,
			PlatformTransactionManager transactionManager,
			@Value("${tenantflow.reminders.enabled:true}") boolean enabled) {
		this.taskRepository = taskRepository;
		this.membershipRepository = membershipRepository;
		this.preferenceService = preferenceService;
		this.notifier = notifier;
		this.automationService = automationService;
		this.tx = new TransactionTemplate(transactionManager);
		this.enabled = enabled;
	}

	@Scheduled(fixedDelayString = "${tenantflow.reminders.interval:PT15M}", initialDelayString = "PT1M")
	public void scheduled() {
		if (!enabled) {
			return;
		}
		try {
			RunResult r = runOnce();
			if (r.dueSoon() + r.overdue() > 0) {
				log.info("Reminders sent: {} due soon, {} overdue", r.dueSoon(), r.overdue());
			}
		} catch (RuntimeException e) {
			log.error("Reminder run failed; it will be retried on the next run", e);
		}
	}

	/** One pass over open tasks due soon or overdue. Safe to call any number of times. */
	public RunResult runOnce() {
		LocalDate serverToday = LocalDate.now();
		List<Task> tasks = tx.execute(s -> {
			List<Task> list = taskRepository.findOpenDueOnOrBefore(serverToday.plusDays(HORIZON_DAYS), PageRequest.of(0, BATCH));
			// Touch the lazy parts used below while the session is open.
			list.forEach(t -> {
				t.getProject().getTenant().getId();
				if (t.getProject().getOwner() != null) {
					t.getProject().getOwner().getName();
				}
				if (t.getAssignee() != null) {
					t.getAssignee().getName();
				}
			});
			return list;
		});
		if (tasks == null || tasks.isEmpty()) {
			return new RunResult(0, 0, 0);
		}
		List<UUID> assignees = tasks.stream().map(Task::getAssignee).filter(Objects::nonNull).map(u -> u.getId()).distinct().toList();
		Map<UUID, NotificationPreference> prefs = preferenceService.loadAll(assignees);
		Map<String, Boolean> membership = new HashMap<>();

		int dueSoon = 0;
		int overdue = 0;
		for (Task t : tasks) {
			if (t.getAssignee() == null) {
				continue;
			}
			UUID userId = t.getAssignee().getId();
			UUID tenantId = t.getProject().getTenant().getId();
			// People who left the workspace are not reminded about its tasks.
			boolean member = membership.computeIfAbsent(userId + ":" + tenantId,
					k -> Boolean.TRUE.equals(tx.execute(s -> membershipRepository.existsByUser_IdAndTenant_Id(userId, tenantId))));
			if (!member) {
				continue;
			}
			NotificationPreference p = prefs.get(userId);
			LocalDate today = LocalDate.now(PreferenceService.zone(p));
			LocalDate due = t.getDueDate();
			String link = "/tenants/" + tenantId + "/projects/" + t.getProject().getId() + "/tasks?task=" + t.getId();
			if (due.isBefore(today)) {
				if (p.isOverdueEnabled() && send(userId, tenantId, "REMINDER_OVERDUE", "Overdue: " + t.getTitle(),
						"Was due " + due + " · " + t.getProject().getName(), link, "reminder:overdue:" + t.getId() + ":" + due)) {
					overdue++;
				}
			} else {
				long daysLeft = ChronoUnit.DAYS.between(today, due);
				if (p.isDueSoonEnabled() && daysLeft <= p.getDueSoonDays()) {
					String when = daysLeft == 0 ? "today" : daysLeft == 1 ? "tomorrow" : "in " + daysLeft + " days";
					if (send(userId, tenantId, "REMINDER_DUE_SOON", "Due " + when + ": " + t.getTitle(),
							t.getProject().getName(), link, "reminder:due:" + t.getId() + ":" + due)) {
						dueSoon++;
					}
				}
			}
		}

		// Scheduled automation: overdue tasks, per workspace (the rule is off unless enabled there).
		Map<UUID, List<Task>> overdueByTenant = tasks.stream()
				.filter(t -> t.getDueDate().isBefore(serverToday))
				.collect(Collectors.groupingBy(t -> t.getProject().getTenant().getId()));
		int checked = 0;
		for (var entry : overdueByTenant.entrySet()) {
			try {
				Integer n = tx.execute(s -> automationService.sweepOverdue(entry.getKey(), new ArrayList<>(entry.getValue()), serverToday));
				checked += n == null ? 0 : n;
			} catch (DataIntegrityViolationException e) {
				// Another instance handled the same tasks at the same moment; nothing is lost.
				log.debug("Concurrent automation run for tenant {}", entry.getKey());
			}
		}
		return new RunResult(dueSoon, overdue, checked);
	}

	private boolean send(UUID userId, UUID tenantId, String type, String title, String body, String link, String key) {
		try {
			return Boolean.TRUE.equals(tx.execute(s -> notifier.notifyOnce(userId, tenantId, type, title, body, link, key)));
		} catch (DataIntegrityViolationException e) {
			return false; // created concurrently by another run: still exactly one
		}
	}
}
