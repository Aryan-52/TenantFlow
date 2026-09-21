package io.github.aryan52.tenantflow.error;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;

public record ApiErrorResponse(
		Instant timestamp,
		int status,
		String error,
		String message,
		String path,
		Map<String, String> fieldErrors
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
		return new ApiErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path, fieldErrors);
	}
}
