package io.github.aryan52.tenantflow.task;

import java.util.Arrays;

/** Cross-project task views linked from the dashboard. All of them exclude done tasks. */
public enum TaskFocus {
	OPEN("open"),
	MINE("mine"),
	OVERDUE("overdue"),
	DUE_SOON("due-soon");

	/** "Due soon" = due today through this many days ahead. */
	public static final int DUE_SOON_DAYS = 7;

	private final String param;

	TaskFocus(String param) {
		this.param = param;
	}

	public static TaskFocus fromParam(String value) {
		if (value == null || value.isBlank()) {
			return OPEN;
		}
		return Arrays.stream(values())
				.filter(f -> f.param.equals(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("focus must be one of: open, mine, overdue, due-soon"));
	}
}
