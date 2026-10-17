package io.github.aryan52.tenantflow.automation;

/**
 * The supported automation rules: fixed, deterministic trigger/action pairs (no user code).
 * Every action only creates notifications, never changes tasks, projects or milestones, so a
 * rule can never trigger another rule (no loops).
 */
public enum AutomationRuleType {
	OVERDUE_NOTIFY_OWNER(
			"A task becomes overdue",
			"Notify the project owner once per task and due date (checked every 15 minutes)"),
	URGENT_NOTIFY_OWNER(
			"A task is created as Urgent or raised to Urgent",
			"Notify the project owner (not when they made the change)"),
	UNBLOCKED_NOTIFY_ASSIGNEE(
			"A task is marked Done",
			"Notify the assignees of tasks it was blocking once nothing else blocks them"),
	MILESTONE_DONE_NOTIFY_ASSIGNEES(
			"A milestone is completed",
			"Notify the assignees of the milestone's unfinished tasks");

	private final String trigger;
	private final String action;

	AutomationRuleType(String trigger, String action) {
		this.trigger = trigger;
		this.action = action;
	}

	public String trigger() {
		return trigger;
	}

	public String action() {
		return action;
	}
}
