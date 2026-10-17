package io.github.aryan52.tenantflow.planning;

import io.github.aryan52.tenantflow.entity.RecurrenceFrequency;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

/**
 * Next due date of a recurring task. Pure date arithmetic on calendar dates (due dates have
 * no time of day), so the result does not depend on any time zone.
 * <ul>
 * <li>DAILY: every {@code interval} days.</li>
 * <li>WEEKLY: on the selected weekdays of every {@code interval}-th week (counted from the
 * current occurrence's week); without weekdays, the same weekday {@code interval} weeks later.</li>
 * <li>MONTHLY: the same day {@code interval} months later; days missing in a shorter month
 * fall on its last day (Jan 31 -> Feb 28).</li>
 * </ul>
 */
public final class Recurrences {

	private Recurrences() {
	}

	public static Optional<LocalDate> next(LocalDate due, RecurrenceFrequency frequency, int interval, int weekdays,
			LocalDate until) {
		if (due == null || frequency == null) {
			return Optional.empty();
		}
		int step = Math.max(1, interval);
		LocalDate next = switch (frequency) {
			case DAILY -> due.plusDays(step);
			case MONTHLY -> due.plusMonths(step);
			case WEEKLY -> nextWeekly(due, step, weekdays & 0x7F);
		};
		return until != null && next.isAfter(until) ? Optional.empty() : Optional.of(next);
	}

	private static LocalDate nextWeekly(LocalDate due, int interval, int mask) {
		if (mask == 0) {
			return due.plusWeeks(interval);
		}
		LocalDate weekStart = due.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		// At most interval weeks + one week ahead holds the next selected weekday.
		for (LocalDate d = due.plusDays(1); ; d = d.plusDays(1)) {
			long weekIndex = ChronoUnit.WEEKS.between(weekStart, d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
			boolean selected = (mask & (1 << (d.getDayOfWeek().getValue() - 1))) != 0;
			if (selected && weekIndex % interval == 0) {
				return d;
			}
		}
	}
}
