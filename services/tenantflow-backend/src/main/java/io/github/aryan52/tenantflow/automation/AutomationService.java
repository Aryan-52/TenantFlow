package io.github.aryan52.tenantflow.automation;

import io.github.aryan52.tenantflow.common.PageResponse;
import io.github.aryan52.tenantflow.common.Pagination;
import io.github.aryan52.tenantflow.entity.Project;
import io.github.aryan52.tenantflow.entity.Task;
import io.github.aryan52.tenantflow.entity.TaskPriority;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import io.github.aryan52.tenantflow.notification.Notifier;
import io.github.aryan52.tenantflow.planning.Milestone;
import io.github.aryan52.tenantflow.planning.TaskDependency;
import io.github.aryan52.tenantflow.planning.TaskDependencyRepository;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.TaskRepository;
import io.github.aryan52.tenantflow.security.AuthenticatedUser;
import io.github.aryan52.tenantflow.security.CurrentUser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the workspace's enabled {@link AutomationRuleType rules}. Event rules run inside the
 * transaction of the change that triggered them (they roll back with it); every run is
 * recorded once per dedupe key, so retries and repeated sweeps never act twice. Rules are
 * always looked up for the workspace of the triggering entity, and recipients must be
 * members of that workspace: one workspace can never act on another.
 */
@Service
@RequiredArgsConstructor
public class AutomationService {

	private static final Logger log = LoggerFactory.getLogger(AutomationService.class);

	public record RuleView(AutomationRuleType type, String trigger, String action, boolean enabled, Instant updatedAt,
			long runs, long failures, Instant lastRunAt) {
	}

	public record RunView(UUID id, AutomationRuleType type, UUID taskId, AutomationRun.Outcome outcome, String message,
			Instant createdAt) {
	}

	private final AutomationRuleRepository ruleRepository;
	private final AutomationRunRepository runRepository;
	private final TaskRepository taskRepository;
	private final TaskDependencyRepository dependencyRepository;
	private final MembershipRepository membershipRepository;
	private final Notifier notifier;

	// ---- Management ---------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<RuleView> rules(UUID tenantId) {
		Map<AutomationRuleType, AutomationRule> rows = new HashMap<>();
		ruleRepository.findByTenantId(tenantId).forEach(r -> rows.put(r.getRuleType(), r));
		Map<AutomationRuleType, Object[]> stats = new HashMap<>();
		for (Object[] row : runRepository.summary(tenantId)) {
			stats.put((AutomationRuleType) row[0], row);
		}
		return Arrays.stream(AutomationRuleType.values()).map(type -> {
			AutomationRule rule = rows.get(type);
			Object[] s = stats.get(type);
			return new RuleView(type, type.trigger(), type.action(), rule != null && rule.isEnabled(),
					rule == null ? null : rule.getUpdatedAt(),
					s == null ? 0 : ((Number) s[1]).longValue(),
					s == null || s[2] == null ? 0 : ((Number) s[2]).longValue(),
					s == null ? null : (Instant) s[3]);
		}).toList();
	}

	@Transactional
	public List<RuleView> setEnabled(UUID tenantId, AutomationRuleType type, boolean enabled, UUID userId) {
		AutomationRule rule = ruleRepository.findByTenantIdAndRuleType(tenantId, type)
				.orElseGet(() -> AutomationRule.builder().tenantId(tenantId).ruleType(type).build());
		rule.setEnabled(enabled);
		rule.setUpdatedBy(userId);
		ruleRepository.save(rule);
		return rules(tenantId);
	}

	@Transactional(readOnly = true)
	public PageResponse<RunView> runs(UUID tenantId, Integer page, Integer size) {
		return PageResponse.of(runRepository.findByTenantIdOrderByCreatedAtDesc(tenantId,
				Pagination.of(page, size, Sort.unsorted())),
				r -> new RunView(r.getId(), r.getRuleType(), r.getTaskId(), r.getOutcome(), r.getMessage(), r.getCreatedAt()));
	}

	// ---- Triggers -----------------------------------------------------------------------

	/** Created as Urgent, or priority raised to Urgent. */
	public void onUrgent(UUID tenantId, Task task) {
		Project project = task.getProject();
		run(tenantId, AutomationRuleType.URGENT_NOTIFY_OWNER, task.getId(),
				"urgent:" + task.getId() + ":v" + task.getVersion(), () -> {
					UUID ownerId = project.getOwner() == null ? null : project.getOwner().getId();
					if (ownerId == null) {
						return skipped("The project has no owner");
					}
					if (ownerId.equals(actorId())) {
						return skipped("The project owner made the change");
					}
					if (!isMember(ownerId, tenantId)) {
						return skipped("The project owner is no longer a member");
					}
					notifier.notifyOnce(ownerId, tenantId, "AUTOMATION_URGENT", "Urgent task: " + task.getTitle(),
							"In " + project.getName(), taskLink(tenantId, task), "auto:urgent:" + task.getId() + ":v" + task.getVersion());
					return success("Notified the owner of " + project.getName() + " about \"" + task.getTitle() + "\"");
				});
	}

	/** A task was marked done: tell assignees of tasks it blocked that are now free to start. */
	public void onTaskCompleted(UUID tenantId, Task task) {
		run(tenantId, AutomationRuleType.UNBLOCKED_NOTIFY_ASSIGNEE, task.getId(),
				"unblocked:" + task.getId() + ":v" + task.getVersion(), () -> {
					List<UUID> blockedIds = dependencyRepository.findByBlockingTaskId(task.getId()).stream()
							.map(TaskDependency::getBlockedTaskId).toList();
					if (blockedIds.isEmpty()) {
						return skipped("\"" + task.getTitle() + "\" was not blocking any task");
					}
					Map<UUID, Integer> stillBlocked = new HashMap<>();
					for (Object[] row : dependencyRepository.countOpenBlockers(blockedIds)) {
						stillBlocked.put((UUID) row[0], ((Number) row[1]).intValue());
					}
					int notified = 0;
					for (Task blocked : taskRepository.findByIdInAndProject_Tenant_Id(blockedIds, tenantId)) {
						if (blocked.getStatus() == TaskStatus.DONE || stillBlocked.getOrDefault(blocked.getId(), 0) > 0
								|| blocked.getAssignee() == null || blocked.getAssignee().getId().equals(actorId())) {
							continue;
						}
						if (notifier.notifyOnce(blocked.getAssignee().getId(), tenantId, "AUTOMATION_UNBLOCKED",
								"Ready to start: " + blocked.getTitle(), "\"" + task.getTitle() + "\" is done",
								taskLink(tenantId, blocked), "auto:unblocked:" + blocked.getId() + ":by:" + task.getId() + ":v" + task.getVersion())) {
							notified++;
						}
					}
					return notified == 0 ? skipped("No assignee to notify") : success("Notified " + notified + " assignee(s) that their task is unblocked");
				});
	}

	public void onMilestoneCompleted(UUID tenantId, Project project, Milestone milestone) {
		run(tenantId, AutomationRuleType.MILESTONE_DONE_NOTIFY_ASSIGNEES, null,
				"milestone:" + milestone.getId() + ":v" + milestone.getVersion(), () -> {
					int notified = 0;
					for (Task t : taskRepository.findByMilestoneIdAndStatusNot(milestone.getId(), TaskStatus.DONE)) {
						if (t.getAssignee() == null || t.getAssignee().getId().equals(actorId())) {
							continue;
						}
						if (notifier.notifyOnce(t.getAssignee().getId(), tenantId, "AUTOMATION_MILESTONE",
								"Milestone completed: " + milestone.getName(), "Your task \"" + t.getTitle() + "\" is still open",
								taskLink(tenantId, t), "auto:milestone:" + milestone.getId() + ":v" + milestone.getVersion() + ":" + t.getId())) {
							notified++;
						}
					}
					return notified == 0 ? skipped("No open assigned tasks in the milestone")
							: success("Notified " + notified + " assignee(s) of open tasks in \"" + milestone.getName() + "\"");
				});
	}

	/** Scheduled: overdue tasks of workspaces with the rule on. One run per task and due date. */
	@Transactional
	public int sweepOverdue(UUID tenantId, List<Task> overdueTasks, LocalDate today) {
		if (!ruleRepository.existsByTenantIdAndRuleTypeAndEnabledTrue(tenantId, AutomationRuleType.OVERDUE_NOTIFY_OWNER)) {
			return 0;
		}
		int handled = 0;
		for (Task task : overdueTasks) {
			if (!task.getProject().getTenant().getId().equals(tenantId) || task.getDueDate() == null
					|| !task.getDueDate().isBefore(today) || task.getStatus() == TaskStatus.DONE) {
				continue;
			}
			handled++;
			run(tenantId, AutomationRuleType.OVERDUE_NOTIFY_OWNER, task.getId(), "overdue:" + task.getId() + ":" + task.getDueDate(), () -> {
				Project project = task.getProject();
				UUID ownerId = project.getOwner() == null ? null : project.getOwner().getId();
				if (ownerId == null || !isMember(ownerId, tenantId)) {
					return skipped("\"" + task.getTitle() + "\" is overdue, but " + project.getName() + " has no owner to notify");
				}
				notifier.notifyOnce(ownerId, tenantId, "AUTOMATION_OVERDUE", "Overdue: " + task.getTitle(),
						"Was due " + task.getDueDate() + " · " + project.getName()
								+ (task.getAssignee() == null ? " · unassigned" : " · " + task.getAssignee().getName()),
						taskLink(tenantId, task), "auto:overdue:" + task.getId() + ":" + task.getDueDate());
				return success("Notified the owner of " + project.getName() + " that \"" + task.getTitle() + "\" is overdue");
			});
		}
		return handled;
	}

	// ---- Engine ---------------------------------------------------------------------------

	private record Result(AutomationRun.Outcome outcome, String message) {
	}

	private static Result success(String message) {
		return new Result(AutomationRun.Outcome.SUCCESS, message);
	}

	private static Result skipped(String message) {
		return new Result(AutomationRun.Outcome.SKIPPED, message);
	}

	private void run(UUID tenantId, AutomationRuleType type, UUID taskId, String dedupeKey, Supplier<Result> action) {
		if (!ruleRepository.existsByTenantIdAndRuleTypeAndEnabledTrue(tenantId, type)
				|| runRepository.existsByTenantIdAndDedupeKey(tenantId, dedupeKey)) {
			return;
		}
		Result result;
		try {
			result = action.get();
		} catch (RuntimeException e) {
			// Surfaced in the execution history; never breaks the change that triggered it.
			log.warn("Automation {} failed in tenant {}", type, tenantId, e);
			result = new Result(AutomationRun.Outcome.FAILED, "The rule could not run. The change itself was saved.");
		}
		runRepository.save(AutomationRun.builder()
				.tenantId(tenantId)
				.ruleType(type)
				.taskId(taskId)
				.outcome(result.outcome())
				.message(truncate(result.message()))
				.dedupeKey(dedupeKey)
				.build());
	}

	private boolean isMember(UUID userId, UUID tenantId) {
		return membershipRepository.existsByUser_IdAndTenant_Id(userId, tenantId);
	}

	private static UUID actorId() {
		return CurrentUser.get().map(AuthenticatedUser::getId).orElse(null);
	}

	private static String taskLink(UUID tenantId, Task task) {
		return "/tenants/" + tenantId + "/projects/" + task.getProject().getId() + "/tasks?task=" + task.getId();
	}

	private static String truncate(String s) {
		return Objects.requireNonNullElse(s, "").length() <= 500 ? s : s.substring(0, 499) + "…";
	}

	/** True when the priority changed to Urgent (or the task is new and Urgent). */
	public static boolean becameUrgent(TaskPriority before, TaskPriority after) {
		return after == TaskPriority.URGENT && before != TaskPriority.URGENT;
	}
}
