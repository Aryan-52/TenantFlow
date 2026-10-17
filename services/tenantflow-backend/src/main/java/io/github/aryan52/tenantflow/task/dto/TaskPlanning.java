package io.github.aryan52.tenantflow.task.dto;

import io.github.aryan52.tenantflow.entity.RecurrenceFrequency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Planning fields of a task (V16), sent as one optional object: omitted from an update =
 * keep the current values (so older clients and bulk edits never clear them); present =
 * replace all of them (a null inside clears that field).
 */
public record TaskPlanning(LocalDate startDate, UUID milestoneId, @Valid Recurrence recurrence) {

	/** {@code weekdays}: WEEKLY only, bit 0 = Monday ... bit 6 = Sunday (0/null = due date's weekday). */
	public record Recurrence(
			@NotNull RecurrenceFrequency frequency,
			@Min(1) @Max(365) Integer interval,
			@Min(0) @Max(127) Integer weekdays,
			LocalDate until) {
	}
}
