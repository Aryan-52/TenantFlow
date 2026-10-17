package io.github.aryan52.tenantflow.planning;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aryan52.tenantflow.entity.RecurrenceFrequency;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RecurrencesTest {

	private static final int MON = 1;
	private static final int WED = 1 << 2;
	private static final int FRI = 1 << 4;

	@Test
	void dailyAndMonthlyStepByTheInterval() {
		assertThat(Recurrences.next(LocalDate.of(2026, 3, 30), RecurrenceFrequency.DAILY, 3, 0, null)).contains(LocalDate.of(2026, 4, 2));
		// Days missing in a shorter month fall on its last day.
		assertThat(Recurrences.next(LocalDate.of(2026, 1, 31), RecurrenceFrequency.MONTHLY, 1, 0, null)).contains(LocalDate.of(2026, 2, 28));
	}

	@Test
	void weeklyUsesTheSelectedWeekdaysOfEveryNthWeek() {
		LocalDate monday = LocalDate.of(2026, 3, 2);
		assertThat(Recurrences.next(monday, RecurrenceFrequency.WEEKLY, 1, MON | WED | FRI, null)).contains(monday.plusDays(2));
		LocalDate friday = monday.plusDays(4);
		assertThat(Recurrences.next(friday, RecurrenceFrequency.WEEKLY, 1, MON | WED | FRI, null)).contains(monday.plusWeeks(1));
		// Every second week: from Friday the next Monday is skipped.
		assertThat(Recurrences.next(friday, RecurrenceFrequency.WEEKLY, 2, MON | WED | FRI, null)).contains(monday.plusWeeks(2));
		// No weekdays: same weekday N weeks later.
		assertThat(Recurrences.next(friday, RecurrenceFrequency.WEEKLY, 2, 0, null)).contains(friday.plusWeeks(2));
	}

	@Test
	void stopsAfterTheEndDate() {
		LocalDate d = LocalDate.of(2026, 3, 2);
		assertThat(Recurrences.next(d, RecurrenceFrequency.DAILY, 1, 0, d)).isEmpty();
		assertThat(Recurrences.next(d, RecurrenceFrequency.DAILY, 1, 0, d.plusDays(1))).contains(d.plusDays(1));
	}
}
