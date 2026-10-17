package io.github.aryan52.tenantflow;

import java.util.Map;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class TenantflowBackendApplication {

	/**
	 * Legacy zone names Java still uses as defaults on some systems, and their current names.
	 * Windows "India Standard Time" becomes Asia/Calcutta in Java. The PostgreSQL driver sends
	 * the JVM's zone to the server on every connection, and PostgreSQL builds without the
	 * old "backward" aliases reject it ("invalid value for parameter TimeZone"), so the
	 * backend could not connect at all.
	 */
	static final Map<String, String> LEGACY_ZONE_NAMES = Map.of("Asia/Calcutta", "Asia/Kolkata");

	public static void main(String[] args) {
		useCurrentZoneName();
		SpringApplication.run(TenantflowBackendApplication.class, args);
	}

	/**
	 * Replaces a legacy default zone name with its current equivalent before anything opens a
	 * database connection. Same zone, same offsets - only the name changes. Any other zone
	 * (UTC in Docker, or whatever -Duser.timezone sets) is left exactly as it is.
	 */
	static void useCurrentZoneName() {
		String current = LEGACY_ZONE_NAMES.get(TimeZone.getDefault().getID());
		if (current != null) {
			TimeZone.setDefault(TimeZone.getTimeZone(current));
		}
	}

	@Bean
	public com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
		com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
		mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		return mapper;
	}
}
