package io.github.aryan52.tenantflow.error;

import io.github.aryan52.tenantflow.auth.DuplicateEmailException;
import io.github.aryan52.tenantflow.auth.InvalidCredentialsException;
import io.github.aryan52.tenantflow.passwordreset.InvalidOrExpiredResetTokenException;
import io.github.aryan52.tenantflow.refreshtoken.InvalidRefreshTokenException;
import io.github.aryan52.tenantflow.user.InvalidCurrentPasswordException;
import io.github.aryan52.tenantflow.user.PasswordUnchangedException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
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
		// Only the application's own validation messages are shown to clients. Library
		// subclasses (e.g. Hibernate query errors, NumberFormatException) can carry internal
		// details, so they are logged and reported as a generic server error.
		if (ex.getClass() != IllegalArgumentException.class) {
			log.error("Unexpected {} on {} {}", ex.getClass().getName(), request.getMethod(), request.getRequestURI(), ex);
			return error(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.", request);
		}
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

	/** Storage outages are logged with their cause; the client only learns it is temporary. */
	@ExceptionHandler(io.github.aryan52.tenantflow.storage.StorageUnavailableException.class)
	public ResponseEntity<ApiErrorResponse> handleStorageUnavailable(
			io.github.aryan52.tenantflow.storage.StorageUnavailableException ex, HttpServletRequest request) {
		log.warn("Object storage failure on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex.getCause());
		return error(HttpStatus.SERVICE_UNAVAILABLE, "File storage is temporarily unavailable. Please try again.", request);
	}

	/** The metadata exists but the bytes are gone: permanent, so say so (and log the key). */
	@ExceptionHandler(io.github.aryan52.tenantflow.storage.StoredObjectMissingException.class)
	public ResponseEntity<ApiErrorResponse> handleStoredObjectMissing(
			io.github.aryan52.tenantflow.storage.StoredObjectMissingException ex, HttpServletRequest request) {
		log.warn("Missing stored object on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
		return error(HttpStatus.NOT_FOUND,
				"This file's contents are no longer available in storage. The file record and its history are kept: upload the original file again to restore it, or delete the entry.", request);
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
		return error(HttpStatus.CONFLICT, ex.getMessage(), request);
	}

	@ExceptionHandler(ForbiddenOperationException.class)
	public ResponseEntity<ApiErrorResponse> handleForbiddenOperation(ForbiddenOperationException ex, HttpServletRequest request) {
		return error(HttpStatus.FORBIDDEN, ex.getMessage(), request);
	}

	@ExceptionHandler(RateLimitExceededException.class)
	public ResponseEntity<ApiErrorResponse> handleRateLimited(RateLimitExceededException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
				.body(ApiErrorResponse.of(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request.getRequestURI()));
	}

	/** Two users edited the same project/task concurrently; the later write lost the race. */
	@ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
	public ResponseEntity<ApiErrorResponse> handleOptimisticLock(Exception ex, HttpServletRequest request) {
		return error(HttpStatus.CONFLICT, "This item was changed by someone else. Reload it and try again.", request);
	}

	/** Never echo constraint names or SQL back to the client. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
		log.warn("Data integrity violation on {} {}", request.getMethod(), request.getRequestURI());
		return error(HttpStatus.CONFLICT, "The request conflicts with existing data", request);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, "Resource not found", request);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
		return error(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", request);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ApiErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
		return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported content type", request);
	}

	@ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
			MissingRequestHeaderException.class, HandlerMethodValidationException.class})
	public ResponseEntity<ApiErrorResponse> handleMissingInput(Exception ex, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "Required request data is missing or invalid", request);
	}

	@ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
	public ResponseEntity<ApiErrorResponse> handleUploadTooLarge(Exception ex, HttpServletRequest request) {
		if (ex instanceof MaxUploadSizeExceededException || ex.getCause() instanceof IllegalStateException) {
			return error(HttpStatus.CONTENT_TOO_LARGE, "The file is too large", request);
		}
		return error(HttpStatus.BAD_REQUEST, "The upload could not be read", request);
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
