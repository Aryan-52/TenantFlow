package io.github.aryan52.tenantflow.error;

import io.github.aryan52.tenantflow.web.RequestCorrelationFilter;
import java.time.Instant;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

/**
 * The single error body shape for every API error. {@code requestId} matches the
 * {@code X-Request-Id} response header and the server logs, so a user-reported error can
 * be traced without exposing stack traces or internal details.
 */
public record ApiErrorResponse(
		Instant timestamp,
		int status,
		String error,
		String message,
		String path,
		Map<String, String> fieldErrors,
		String requestId
) {

	public static ApiErrorResponse of(HttpStatus status, String message, String path) {
		return of(status, message, path, Map.of());
	}

	public static ApiErrorResponse of(
			HttpStatus status,
			String message,
			String path,
			Map<String, String> fieldErrors
	) {
		return new ApiErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path, fieldErrors,
				MDC.get(RequestCorrelationFilter.MDC_KEY));
	}
}
