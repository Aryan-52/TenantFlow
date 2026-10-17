package io.github.aryan52.tenantflow.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

/** Only the application's own IllegalArgumentException messages may reach clients. */
class IllegalArgumentHandlingTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
	private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");

	@Test
	void plainIllegalArgumentIsAValidationError() {
		var response = handler.handleIllegalArgument(new IllegalArgumentException("Title is required"), request);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().message()).isEqualTo("Title is required");
	}

	@Test
	void subclassMessagesAreNeverExposed() {
		// e.g. a library query error carrying internal details
		var response = handler.handleIllegalArgument(new NumberFormatException("select t from Task t where secret"), request);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody().message()).isEqualTo("Something went wrong. Please try again.");
	}
}
