package io.github.aryan52.tenantflow.oauth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aryan52.tenantflow.auth.dto.LoginRequest;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.repository.UserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Google OAuth must be entirely config-driven: absent client-id/secret, the app starts
 * normally and the OAuth entry point simply doesn't exist; present, it's wired in. In
 * neither case may plain email/password auth stop working.
 */
class OAuthConfigurationIntegrationTests {

	@Nested
	@SpringBootTest(properties = {
			"spring.datasource.url=jdbc:h2:mem:tenantflow_oauth_unconfigured;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
			"spring.datasource.driver-class-name=org.h2.Driver",
			"spring.datasource.username=sa",
			"spring.datasource.password=",
			"spring.jpa.hibernate.ddl-auto=create-drop",
			"spring.flyway.enabled=false",
			"tenantflow.jwt.secret=test-oauth-off-secret-test-oauth-off-secret",
			"tenantflow.jwt.expiration-seconds=3600",
			"tenantflow.oauth.google.client-id=",
			"tenantflow.oauth.google.client-secret="
	})
	@AutoConfigureMockMvc
	class WithoutGoogleCredentials {

		@Autowired
		private MockMvc mockMvc;

		@Autowired
		private ObjectMapper objectMapper;

		@Autowired
		private UserRepository userRepository;

		@Autowired
		private PasswordEncoder passwordEncoder;

		@Test
		void applicationStartsAndGoogleLoginEndpointDoesNotExist() throws Exception {
			mockMvc.perform(get("/oauth2/authorization/google"))
					.andExpect(status().isNotFound());
		}

		@Test
		void emailPasswordLoginStillWorksWithOAuthUnconfigured() throws Exception {
			userRepository.save(User.builder()
					.email("still-works@example.com")
					.passwordHash(passwordEncoder.encode("Password123!"))
					.name("Still Works")
					.build());

			LoginRequest request = new LoginRequest("still-works@example.com", "Password123!", false);
			mockMvc.perform(post("/api/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isOk());
		}
	}

	@Nested
	@SpringBootTest(properties = {
			"spring.datasource.url=jdbc:h2:mem:tenantflow_oauth_configured;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
			"spring.datasource.driver-class-name=org.h2.Driver",
			"spring.datasource.username=sa",
			"spring.datasource.password=",
			"spring.jpa.hibernate.ddl-auto=create-drop",
			"spring.flyway.enabled=false",
			"tenantflow.jwt.secret=test-oauth-on-secret-test-oauth-on-secret",
			"tenantflow.jwt.expiration-seconds=3600",
			"tenantflow.oauth.google.client-id=test-client-id.apps.googleusercontent.com",
			"tenantflow.oauth.google.client-secret=test-client-secret",
			"tenantflow.oauth.google.frontend-redirect-uri=http://localhost:3000/oauth-callback"
	})
	@AutoConfigureMockMvc
	class WithGoogleCredentialsConfigured {

		@Autowired
		private MockMvc mockMvc;

		@Autowired
		private ObjectMapper objectMapper;

		@Autowired
		private UserRepository userRepository;

		@Autowired
		private PasswordEncoder passwordEncoder;

		@Test
		void applicationStartsAndGoogleLoginEndpointRedirectsToGoogle() throws Exception {
			mockMvc.perform(get("/oauth2/authorization/google"))
					.andExpect(status().is3xxRedirection());
		}

		@Test
		void emailPasswordLoginStillWorksWithOAuthConfigured() throws Exception {
			userRepository.save(User.builder()
					.email("both-work@example.com")
					.passwordHash(passwordEncoder.encode("Password123!"))
					.name("Both Work")
					.build());

			LoginRequest request = new LoginRequest("both-work@example.com", "Password123!", false);
			mockMvc.perform(post("/api/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isOk());
		}
	}
}
