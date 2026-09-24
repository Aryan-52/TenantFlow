package io.github.aryan52.tenantflow.error;

import io.github.aryan52.tenantflow.auth.DuplicateEmailException;
import io.github.aryan52.tenantflow.auth.InvalidCredentialsException;
import io.github.aryan52.tenantflow.passwordreset.InvalidOrExpiredResetTokenException;
import io.github.aryan52.tenantflow.refreshtoken.InvalidRefreshTokenException;
import io.github.aryan52.tenantflow.user.InvalidCurrentPasswordException;
import io.github.aryan52.tenantflow.user.PasswordUnchangedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

	@ExceptionHandler(InvalidCurrentPasswordException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidCurrentPassword(
			InvalidCurrentPasswordException ex,
			HttpServletRequest request
	) {
		// 400, not 401: the caller's JWT/session is perfectly valid here - only the
		// submitted current-password value was wrong. Returning 401 would make the
		// frontend's global "session expired" handling log the user out, which is
		// exactly what this endpoint must never do.
		return fieldError(HttpStatus.BAD_REQUEST, ex.getMessage(), "currentPassword", request);
	}

	@ExceptionHandler(PasswordUnchangedException.class)
	public ResponseEntity<ApiErrorResponse> handlePasswordUnchanged(
			PasswordUnchangedException ex,
			HttpServletRequest request
	) {
		return fieldError(HttpStatus.BAD_REQUEST, ex.getMessage(), "newPassword", request);
	}

	@ExceptionHandler(InvalidOrExpiredResetTokenException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidResetToken(
			InvalidOrExpiredResetTokenException ex,
			HttpServletRequest request
	) {
		return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
	}

	@ExceptionHandler(InvalidRefreshTokenException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidRefreshToken(
			InvalidRefreshTokenException ex,
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

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
			MethodArgumentTypeMismatchException ex,
			HttpServletRequest request
	) {
		return error(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'", request);
	}

	// These two exception types are normally resolved by RestAccessDeniedHandler /
	// RestAuthenticationEntryPoint via the Spring Security filter chain. They are handled
	// explicitly here too (with matching messages) so that the generic Exception handler
	// below can never intercept them first and mask a 401/403 as a 500.
	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiErrorResponse> handleAccessDenied(
			AccessDeniedException ex,
			HttpServletRequest request
	) {
		return error(HttpStatus.FORBIDDEN, "Access denied", request);
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ApiErrorResponse> handleAuthentication(
			AuthenticationException ex,
			HttpServletRequest request
	) {
		return error(HttpStatus.UNAUTHORIZED, "Authentication required", request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(
			Exception ex,
			HttpServletRequest request
	) {
		log.error("Unhandled exception while processing {} {}", request.getMethod(), request.getRequestURI(), ex);
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
	}

	private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message, HttpServletRequest request) {
		return ResponseEntity
				.status(status)
				.body(ApiErrorResponse.of(status, message, request.getRequestURI()));
	}

	private ResponseEntity<ApiErrorResponse> fieldError(HttpStatus status, String message, String field, HttpServletRequest request) {
		return ResponseEntity
				.status(status)
				.body(ApiErrorResponse.of(status, message, request.getRequestURI(), Map.of(field, message)));
	}
}
