package io.github.aryan52.tenantflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.security.JwtService;
import io.github.aryan52.tenantflow.user.dto.ChangePasswordRequest;
import io.github.aryan52.tenantflow.user.dto.UpdateProfileRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_user_api;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.show-sql=false",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-user-secret-test-user-secret-test-user-secret",
		"tenantflow.jwt.expiration-seconds=3600"
})
@AutoConfigureMockMvc
class UserIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtService jwtService;

	private User user;
	private String token;

	@BeforeEach
	void setup() {
		userRepository.deleteAll();
		user = userRepository.save(User.builder()
				.email("me@example.com")
				.name("Me")
				.passwordHash(passwordEncoder.encode("Password123!"))
				.build());
		token = jwtService.generateToken(user);
	}

	@Test
	void getCurrentUserSucceeds() throws Exception {
		mockMvc.perform(get("/api/users/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("me@example.com"))
				.andExpect(jsonPath("$.name").value("Me"))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void getCurrentUserFailsIfNotAuthenticated() throws Exception {
		mockMvc.perform(get("/api/users/me"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void updatesProfileNameSuccessfully() throws Exception {
		UpdateProfileRequest request = new UpdateProfileRequest("Updated Name");

		mockMvc.perform(put("/api/users/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Updated Name"))
				.andExpect(jsonPath("$.email").value("me@example.com"));

		User reloaded = userRepository.findById(user.getId()).orElseThrow();
		assertThat(reloaded.getName()).isEqualTo("Updated Name");
	}

	@Test
	void rejectsBlankNameOnProfileUpdate() throws Exception {
		UpdateProfileRequest request = new UpdateProfileRequest("  ");

		mockMvc.perform(put("/api/users/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.name").exists());
	}

	@Test
	void updateProfileFailsIfNotAuthenticated() throws Exception {
		UpdateProfileRequest request = new UpdateProfileRequest("Someone Else");

		mockMvc.perform(put("/api/users/me")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void changesPasswordSuccessfully() throws Exception {
		ChangePasswordRequest request = new ChangePasswordRequest("Password123!", "NewPassword456!");

		mockMvc.perform(put("/api/users/me/password")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isNoContent());

		User reloaded = userRepository.findById(user.getId()).orElseThrow();
		assertThat(passwordEncoder.matches("NewPassword456!", reloaded.getPasswordHash())).isTrue();
		assertThat(passwordEncoder.matches("Password123!", reloaded.getPasswordHash())).isFalse();
	}

	@Test
	void rejectsPasswordChangeWithWrongCurrentPassword() throws Exception {
		ChangePasswordRequest request = new ChangePasswordRequest("WrongPassword123!", "NewPassword456!");

		mockMvc.perform(put("/api/users/me/password")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Current password is incorrect"));
	}

	@Test
	void rejectsWeakNewPassword() throws Exception {
		ChangePasswordRequest request = new ChangePasswordRequest("Password123!", "weak");

		mockMvc.perform(put("/api/users/me/password")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.newPassword").exists());
	}
}
