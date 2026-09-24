package io.github.aryan52.tenantflow.user;

import io.github.aryan52.tenantflow.auth.dto.AuthResponse;
import io.github.aryan52.tenantflow.security.AuthenticatedUser;
import io.github.aryan52.tenantflow.user.dto.ChangePasswordRequest;
import io.github.aryan52.tenantflow.user.dto.UpdateProfileRequest;
import io.github.aryan52.tenantflow.user.dto.UserProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@GetMapping("/me")
	public UserProfileResponse getCurrentUser(@AuthenticationPrincipal AuthenticatedUser currentUser) {
		return userService.getProfile(currentUser.getId());
	}

	@PutMapping("/me")
	public UserProfileResponse updateProfile(
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Valid @RequestBody UpdateProfileRequest request
	) {
		return userService.updateProfile(currentUser.getId(), request);
	}

	/** Returns a freshly-issued access token (same shape as login) so the calling session
	 * keeps working after the password-change-triggered token_version bump. See
	 * UserService.changePassword for why. */
	@PutMapping("/me/password")
	public AuthResponse changePassword(
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Valid @RequestBody ChangePasswordRequest request
	) {
		return userService.changePassword(currentUser.getId(), request);
	}
}
