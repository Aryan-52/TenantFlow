package io.github.aryan52.tenantflow.error;

import io.github.aryan52.tenantflow.auth.DuplicateEmailException;
import io.github.aryan52.tenantflow.auth.InvalidCredentialsException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(DuplicateEmailException.class)
	public ResponseEntity<ApiErrorResponse> handleDuplicateEmail(
			DuplicateEmailException ex,
			HttpServletRequest request
	) {
		return error(HttpStatus.CONFLICT, ex.getMessage(), request);
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
			InvalidCredentialsException ex,
			HttpServletRequest request
	) {
		return error(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(
			MethodArgumentNotValidException ex,
			HttpServletRequest request
	) {
		Map<String, String> fieldErrors = new TreeMap<>();
		ex.getBindingResult().getFieldErrors().forEach(error ->
				fieldErrors.put(error.getField(), error.getDefaultMessage())
		);

		ApiErrorResponse body = ApiErrorResponse.of(
				HttpStatus.BAD_REQUEST,
				"Validation failed",
				request.getRequestURI(),
				fieldErrors
		);
		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
			HttpMessageNotReadableException ex,
			HttpServletRequest request
	) {
		return error(HttpStatus.BAD_REQUEST, "Request body is invalid", request);
	}

	@ExceptionHandler(DuplicateTenantException.class)
	public ResponseEntity<ApiErrorResponse> handleDuplicateTenant(
			DuplicateTenantException ex,
			HttpServletRequest request
	) {
		return error(HttpStatus.CONFLICT, ex.getMessage(), request);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
			ResourceNotFoundException ex,
			HttpServletRequest request
	) {
		return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
	}

	@ExceptionHandler(DuplicateMembershipException.class)
	public ResponseEntity<ApiErrorResponse> handleDuplicateMembership(
			DuplicateMembershipException ex,
			HttpServletRequest request
	) {
		return error(HttpStatus.CONFLICT, ex.getMessage(), request);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
			IllegalArgumentException ex,
			HttpServletRequest request
	) {
		return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
	}

	private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message, HttpServletRequest request) {
		return ResponseEntity
				.status(status)
				.body(ApiErrorResponse.of(status, message, request.getRequestURI()));
	}
}
