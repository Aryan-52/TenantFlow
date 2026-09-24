package io.github.aryan52.tenantflow.oauth;

import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.repository.UserRepository;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hooked into Spring Security's OIDC login as the userInfoEndpoint().oidcUserService(...).
 * Its only job is the find-or-create side effect against our own `users` table; the
 * returned OidcUser is unmodified so OAuth2LoginSuccessHandler can read the verified
 * email back out of it afterwards.
 *
 * Account-linking strategy: an existing local (or Google) account is matched purely by
 * verified email address. Google's `email_verified` claim is required to be true before
 * we trust the email for linking or creation at all - an unverified email is refused
 * outright, since silently trusting it would let someone claim an existing TenantFlow
 * account they don't actually control.
 */
@Service
@RequiredArgsConstructor
public class GoogleUserProvisioningService implements OAuth2UserService<OidcUserRequest, OidcUser> {

	private final OidcUserService delegate = new OidcUserService();
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
		OidcUser oidcUser = delegate.loadUser(userRequest);

		if (!Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
			throw new OAuth2AuthenticationException(
					new OAuth2Error("email_not_verified"),
					"Google did not report this email address as verified."
			);
		}

		String email = normalizeEmail(oidcUser.getEmail());
		String name = oidcUser.getFullName() != null && !oidcUser.getFullName().isBlank()
				? oidcUser.getFullName()
				: email;

		userRepository.findByEmail(email).orElseGet(() -> userRepository.save(
				User.builder()
						.email(email)
						.name(name)
						// Unusable placeholder: this account can only ever sign in via Google
						// unless the owner sets a real password through "forgot password" later.
						.passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
						.authProvider("GOOGLE")
						.build()
		));

		return oidcUser;
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
