package io.github.aryan52.tenantflow.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class TimestampsTest {

	@Test
	void everyValueIsLaterThanThePreviousOneAndMicrosecondPrecise() {
		Instant previous = Timestamps.now();
		for (int i = 0; i < 10_000; i++) {
			Instant next = Timestamps.now();
			assertThat(next).isAfter(previous);
			assertThat(next.getNano() % 1000).isZero();
			previous = next;
		}
		// Never far ahead of the real clock.
		assertThat(Duration.between(Instant.now(), previous).abs()).isLessThan(Duration.of(100, ChronoUnit.MILLIS));
	}
}
