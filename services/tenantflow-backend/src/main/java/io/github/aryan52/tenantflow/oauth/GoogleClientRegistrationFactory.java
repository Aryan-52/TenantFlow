package io.github.aryan52.tenantflow.oauth;

import io.github.aryan52.tenantflow.config.GoogleOAuthProperties;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * Builds the Google ClientRegistrationRepository from our own configuration
 * (GoogleOAuthProperties) instead of Spring Boot's spring.security.oauth2.client.*
 * auto-configuration, so we have one clear on/off switch: whether both client-id and
 * client-secret are present. See SecurityConfig, which only wires .oauth2Login(...) into
 * the filter chain at all when GoogleOAuthProperties.isConfigured() is true - so this
 * factory is only ever called when both values are present.
 */
public final class GoogleClientRegistrationFactory {

	private static final String REGISTRATION_ID = "google";

	private GoogleClientRegistrationFactory() {
	}

	public static ClientRegistrationRepository build(GoogleOAuthProperties properties) {
		ClientRegistration registration = CommonOAuth2Provider.GOOGLE.getBuilder(REGISTRATION_ID)
				.clientId(properties.clientId())
				.clientSecret(properties.clientSecret())
				.build();
		return new InMemoryClientRegistrationRepository(registration);
	}
}
