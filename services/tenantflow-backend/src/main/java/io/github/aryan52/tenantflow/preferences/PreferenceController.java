package io.github.aryan52.tenantflow.preferences;

import io.github.aryan52.tenantflow.preferences.PreferenceService.Preferences;
import io.github.aryan52.tenantflow.preferences.PreferenceService.PreferencesRequest;
import io.github.aryan52.tenantflow.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's own preferences only. */
@RestController
@RequestMapping("/api/users/me/preferences")
@RequiredArgsConstructor
public class PreferenceController {

	private final PreferenceService preferenceService;

	@GetMapping
	public Preferences get(@AuthenticationPrincipal AuthenticatedUser user) {
		return preferenceService.get(user.getId());
	}

	@PutMapping
	public Preferences update(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody PreferencesRequest request) {
		return preferenceService.update(user.getId(), request);
	}
}
