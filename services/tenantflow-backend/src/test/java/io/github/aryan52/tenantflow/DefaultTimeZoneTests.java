package io.github.aryan52.tenantflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Regression: on Windows set to India Standard Time, Java's default zone is Asia/Calcutta,
 * which the bundled PostgreSQL rejects when the JDBC driver connects. */
class DefaultTimeZoneTests {

	private TimeZone original;

	@BeforeEach
	void remember() {
		original = TimeZone.getDefault();
	}

	@AfterEach
	void restore() {
		TimeZone.setDefault(original);
	}

	@Test
	void legacyIndiaZoneNameIsReplacedWithTheCurrentOne() {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Calcutta"));
		TenantflowBackendApplication.useCurrentZoneName();
		assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Kolkata");
		assertThat(TimeZone.getDefault().getRawOffset()).isEqualTo(TimeZone.getTimeZone("Asia/Calcutta").getRawOffset());
	}

	@Test
	void otherZonesAreLeftAlone() {
		for (String zone : new String[] {"UTC", "Asia/Kolkata", "Europe/Berlin"}) {
			TimeZone.setDefault(TimeZone.getTimeZone(zone));
			TenantflowBackendApplication.useCurrentZoneName();
			assertThat(TimeZone.getDefault().getID()).isEqualTo(zone);
		}
	}
}
