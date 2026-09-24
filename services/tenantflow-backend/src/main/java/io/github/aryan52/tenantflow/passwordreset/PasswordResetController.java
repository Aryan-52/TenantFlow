package io.github.aryan52.tenantflow.passwordreset;

import io.github.aryan52.tenantflow.passwordreset.dto.ForgotPasswordRequest;
import io.github.aryan52.tenantflow.passwordreset.dto.MessageResponse;
import io.github.aryan52.tenantflow.passwordreset.dto.ResetPasswordRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class PasswordResetController {

	private static final String GENERIC_MESSAGE =
			"If an account exists for this email, you will receive password reset instructions.";

	private final PasswordResetService passwordResetService;

	@PostMapping("/forgot-password")
	public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		// Always the same response, in the same amount of code paths, regardless of whether
		// the account exists - see PasswordResetService for the enumeration-safe lookup.
		passwordResetService.requestReset(request.email());
		return new MessageResponse(GENERIC_MESSAGE);
	}

	@PostMapping("/reset-password")
	public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		passwordResetService.resetPassword(request.token(), request.newPassword());
		return ResponseEntity.ok(new MessageResponse("Your password has been reset. Please sign in with your new password."));
	}
}
