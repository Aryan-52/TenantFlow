package io.github.aryan52.tenantflow.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aryan52.tenantflow.entity.PasswordResetToken;
import io.github.aryan52.tenantflow.entity.RefreshToken;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.passwordreset.dto.ForgotPasswordRequest;
import io.github.aryan52.tenantflow.passwordreset.dto.ResetPasswordRequest;
import io.github.aryan52.tenantflow.repository.PasswordResetTokenRepository;
import io.github.aryan52.tenantflow.repository.RefreshTokenRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.security.SecureTokens;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_password_reset;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.show-sql=false",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-reset-secret-test-reset-secret-test-reset",
		"tenantflow.jwt.expiration-seconds=3600",
		"tenantflow.refresh-token.expiration-days=30",
		"tenantflow.password-reset.expiration-minutes=30",
		"tenantflow.password-reset.url-template=http://localhost:3000/reset-password?token={token}",
		"tenantflow.cookie.secure=false",
		"tenantflow.cookie.same-site=Lax"
})
@AutoConfigureMockMvc
class PasswordResetIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordResetTokenRepository passwordResetTokenRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private User user;

	@BeforeEach
	void setup() {
		refreshTokenRepository.deleteAll();
		passwordResetTokenRepository.deleteAll();
		userRepository.deleteAll();
		user = userRepository.save(User.builder()
				.email("reset-me@example.com")
				.passwordHash(passwordEncoder.encode("OldPassword123!"))
				.name("Reset Me")
				.build());
	}

	@Test
	void forgotPasswordForUnknownEmailReturnsGenericSuccessAndCreatesNoToken() throws Exception {
		ForgotPasswordRequest request = new ForgotPasswordRequest("nobody-here@example.com");

		mockMvc.perform(post("/api/auth/forgot-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(
						"If an account exists for this email, you will receive password reset instructions."));

		assertThat(passwordResetTokenRepository.findAll()).isEmpty();
	}

	@Test
	void forgotPasswordForKnownEmailReturnsSameGenericMessageAndCreatesAToken() throws Exception {
		ForgotPasswordRequest request = new ForgotPasswordRequest("reset-me@example.com");

		mockMvc.perform(post("/api/auth/forgot-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(
						"If an account exists for this email, you will receive password reset instructions."));

		List<PasswordResetToken> tokens = passwordResetTokenRepository.findByUserIdAndUsedAtIsNull(user.getId());
		assertThat(tokens).hasSize(1);
		// The raw token must never be persisted - only its hash.
		assertThat(tokens.get(0).getTokenHash()).doesNotContain("nobody-here");
	}

	@Test
	void secondForgotPasswordRequestInvalidatesTheFirstToken() throws Exception {
		ForgotPasswordRequest request = new ForgotPasswordRequest("reset-me@example.com");

		mockMvc.perform(post("/api/auth/forgot-password")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request))).andExpect(status().isOk());
		mockMvc.perform(post("/api/auth/forgot-password")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request))).andExpect(status().isOk());

		// The first token is now used (marked by the second request), and the second
		// request has issued a fresh token that remains active.
		assertThat(passwordResetTokenRepository.findByUserIdAndUsedAtIsNull(user.getId())).hasSize(1);
		assertThat(passwordResetTokenRepository.findAll()).hasSize(2);
	}

	@Test
	void resetPasswordWithGarbageTokenIsRejected() throws Exception {
		ResetPasswordRequest request = new ResetPasswordRequest("not-a-real-token", "NewPassword123!");

		mockMvc.perform(post("/api/auth/reset-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("This password reset link is invalid or has expired."));
	}

	@Test
	void resetPasswordWithExpiredTokenIsRejected() throws Exception {
		String rawToken = SecureTokens.generate();
		passwordResetTokenRepository.save(PasswordResetToken.builder()
				.userId(user.getId())
				.tokenHash(SecureTokens.hash(rawToken))
				.expiresAt(Instant.now().minus(1, ChronoUnit.MINUTES))
				.build());

		ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword123!");

		mockMvc.perform(post("/api/auth/reset-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("This password reset link is invalid or has expired."));
	}

	@Test
	void resetPasswordEnforcesThePasswordPolicy() throws Exception {
		String rawToken = SecureTokens.generate();
		passwordResetTokenRepository.save(PasswordResetToken.builder()
				.userId(user.getId())
				.tokenHash(SecureTokens.hash(rawToken))
				.expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
				.build());

		ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "weak");

		mockMvc.perform(post("/api/auth/reset-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.newPassword").exists());
	}

	@Test
	void successfulResetChangesPasswordAndTokenCannotBeReused() throws Exception {
		RefreshToken activeSession = refreshTokenRepository.save(RefreshToken.builder()
				.userId(user.getId())
				.tokenHash(SecureTokens.hash(SecureTokens.generate()))
				.expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
				.build());

		String rawToken = SecureTokens.generate();
		passwordResetTokenRepository.save(PasswordResetToken.builder()
				.userId(user.getId())
				.tokenHash(SecureTokens.hash(rawToken))
				.expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
				.build());

		ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword123!");

		mockMvc.perform(post("/api/auth/reset-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk());

		User reloaded = userRepository.findById(user.getId()).orElseThrow();
		assertThat(passwordEncoder.matches("NewPassword123!", reloaded.getPasswordHash())).isTrue();
		assertThat(passwordEncoder.matches("OldPassword123!", reloaded.getPasswordHash())).isFalse();
		assertThat(reloaded.getTokenVersion()).isEqualTo(1);

		// Any previously "remembered" session is revoked by a reset.
		RefreshToken reloadedSession = refreshTokenRepository.findById(activeSession.getId()).orElseThrow();
		assertThat(reloadedSession.isRevoked()).isTrue();

		// The same token cannot be used a second time.
		mockMvc.perform(post("/api/auth/reset-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("This password reset link is invalid or has expired."));
	}
}
