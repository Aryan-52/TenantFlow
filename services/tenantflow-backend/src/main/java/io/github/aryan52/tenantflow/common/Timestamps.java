package io.github.aryan52.tenantflow.common;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Strictly increasing timestamps for records whose order is shown to people (activity, audit,
 * notifications, comments, "recently updated"). The system clock can advance in coarse steps
 * (about 0.5-1.5 ms on Windows), so two events recorded one after the other could otherwise
 * share a timestamp and come back in either order. Values are microsecond-precise, matching
 * what PostgreSQL stores, and never run ahead of the real clock by more than the number of
 * events recorded within one clock tick.
 */
public final class Timestamps {

	private static final AtomicReference<Instant> LAST = new AtomicReference<>(Instant.EPOCH);

	private Timestamps() {
	}

	public static Instant now() {
		while (true) {
			Instant last = LAST.get();
			Instant candidate = Instant.now().truncatedTo(ChronoUnit.MICROS);
			if (!candidate.isAfter(last)) {
				candidate = last.plus(1, ChronoUnit.MICROS);
			}
			if (LAST.compareAndSet(last, candidate)) {
				return candidate;
			}
		}
	}
}
