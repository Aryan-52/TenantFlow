package io.github.aryan52.tenantflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aryan52.tenantflow.auth.dto.AuthResponse;
import io.github.aryan52.tenantflow.auth.dto.LoginRequest;
import io.github.aryan52.tenantflow.auth.dto.RegisterRequest;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.security.JwtService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_auth;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.show-sql=false",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-auth-secret-test-auth-secret-test-auth-secret",
		"tenantflow.jwt.expiration-seconds=3600"
})
@AutoConfigureMockMvc
@Import(AuthIntegrationTests.ProtectedEndpointConfig.class)
class AuthIntegrationTests {

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

	@BeforeEach
	void deleteUsers() {
		userRepository.deleteAll();
	}

	@Test
	void registersUserSuccessfully() throws Exception {
		RegisterRequest request = new RegisterRequest("Test@Example.com", "Password123!", "Test User");

		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresInSeconds").value(3600))
				.andExpect(jsonPath("$.email").value("test@example.com"))
				.andExpect(jsonPath("$.name").value("Test User"))
				.andReturn();

		AuthResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
		User savedUser = userRepository.findById(response.userId()).orElseThrow();

		assertThat(savedUser.getEmail()).isEqualTo("test@example.com");
		assertThat(savedUser.getPasswordHash()).isNotEqualTo("Password123!");
		assertThat(passwordEncoder.matches("Password123!", savedUser.getPasswordHash())).isTrue();
	}

	@Test
	void rejectsDuplicateEmail() throws Exception {
		RegisterRequest request = new RegisterRequest("duplicate@example.com", "Password123!", "First User");

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.message").value("An account with this email already exists. Please sign in instead."));
	}

	@Test
	void logsUserInSuccessfully() throws Exception {
		User user = userRepository.save(User.builder()
				.email("login@example.com")
				.passwordHash(passwordEncoder.encode("Password123!"))
				.name("Login User")
				.build());
		LoginRequest request = new LoginRequest("login@example.com", "Password123!", false);

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.userId").value(user.getId().toString()))
				.andExpect(jsonPath("$.email").value("login@example.com"))
				.andExpect(jsonPath("$.name").value("Login User"));
	}

	@Test
	void rejectsInvalidPassword() throws Exception {
		userRepository.save(User.builder()
				.email("wrong-password@example.com")
				.passwordHash(passwordEncoder.encode("Password123!"))
				.name("Wrong Password")
				.build());
		LoginRequest request = new LoginRequest("wrong-password@example.com", "WrongPassword123!", false);

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}

	@Test
	void rejectsInvalidRegisterRequest() throws Exception {
		RegisterRequest request = new RegisterRequest("invalid-email", "short", "");

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.message").value("Validation failed"))
				.andExpect(jsonPath("$.fieldErrors.email").exists())
				.andExpect(jsonPath("$.fieldErrors.password").exists())
				.andExpect(jsonPath("$.fieldErrors.name").exists());
	}

	@Test
	void rejectsInvalidLoginRequest() throws Exception {
		LoginRequest request = new LoginRequest("invalid-email", "", false);

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.message").value("Validation failed"))
				.andExpect(jsonPath("$.fieldErrors.email").exists())
				.andExpect(jsonPath("$.fieldErrors.password").exists());
	}

	@Test
	void rejectsProtectedEndpointWithoutJwt() throws Exception {
		mockMvc.perform(get("/api/protected-test"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.message").value("Authentication required"));
	}

	@Test
	void acceptsProtectedEndpointWithValidJwt() throws Exception {
		User user = userRepository.save(User.builder()
				.email("jwt@example.com")
				.passwordHash(passwordEncoder.encode("Password123!"))
				.name("JWT User")
				.build());
		String token = jwtService.generateToken(user);

		mockMvc.perform(get("/api/protected-test")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.email").value("jwt@example.com"));
	}

	@TestConfiguration
	static class ProtectedEndpointConfig {

		@RestController
		static class ProtectedEndpointController {

			@GetMapping("/api/protected-test")
			Map<String, String> protectedEndpoint(Authentication authentication) {
				return Map.of("email", authentication.getName());
			}
		}
	}
}
