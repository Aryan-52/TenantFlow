package io.github.aryan52.tenantflow.user;

import io.github.aryan52.tenantflow.security.AuthenticatedUser;
import io.github.aryan52.tenantflow.user.dto.ChangePasswordRequest;
import io.github.aryan52.tenantflow.user.dto.UpdateProfileRequest;
import io.github.aryan52.tenantflow.user.dto.UserProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

	@PutMapping("/me/password")
	public ResponseEntity<Void> changePassword(
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Valid @RequestBody ChangePasswordRequest request
	) {
		userService.changePassword(currentUser.getId(), request);
		return ResponseEntity.noContent().build();
	}
}
