package io.github.aryan52.tenantflow.refreshtoken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aryan52.tenantflow.auth.dto.LoginRequest;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** "Remember me": login issues an HttpOnly refresh cookie, /api/auth/refresh rotates it
 * into a new access token + new cookie, and reuse of an already-rotated (revoked) token
 * is treated as theft and kills the whole session family. */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_refresh;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.show-sql=false",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-refresh-secret-test-refresh-secret-test",
		"tenantflow.jwt.expiration-seconds=3600",
		"tenantflow.refresh-token.expiration-days=30",
		"tenantflow.cookie.secure=false",
		"tenantflow.cookie.same-site=Lax"
})
@AutoConfigureMockMvc
class RefreshTokenIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@BeforeEach
	void setup() {
		refreshTokenRepository.deleteAll();
		userRepository.deleteAll();
		userRepository.save(User.builder()
				.email("remember@example.com")
				.passwordHash(passwordEncoder.encode("Password123!"))
				.name("Remember Me")
				.build());
	}

	@Test
	void plainLoginDoesNotSetRefreshCookie() throws Exception {
		LoginRequest request = new LoginRequest("remember@example.com", "Password123!", false);

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(cookie().doesNotExist(RefreshCookieUtil.COOKIE_NAME));

		assertThat(refreshTokenRepository.findAll()).isEmpty();
	}

	@Test
	void rememberMeLoginSetsRefreshCookieAndCanBeRefreshed() throws Exception {
		LoginRequest request = new LoginRequest("remember@example.com", "Password123!", true);

		MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(cookie().exists(RefreshCookieUtil.COOKIE_NAME))
				.andExpect(cookie().httpOnly(RefreshCookieUtil.COOKIE_NAME, true))
				.andReturn();

		assertThat(refreshTokenRepository.findAll()).hasSize(1);

		Cookie refreshCookie = loginResult.getResponse().getCookie(RefreshCookieUtil.COOKIE_NAME);
		assertThat(refreshCookie).isNotNull();

		MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.email").value("remember@example.com"))
				.andExpect(cookie().exists(RefreshCookieUtil.COOKIE_NAME))
				.andReturn();

		Cookie rotatedCookie = refreshResult.getResponse().getCookie(RefreshCookieUtil.COOKIE_NAME);
		assertThat(rotatedCookie).isNotNull();
		assertThat(rotatedCookie.getValue()).isNotEqualTo(refreshCookie.getValue());
	}

	@Test
	void reusingARotatedRefreshTokenRevokesTheWholeSessionFamily() throws Exception {
		LoginRequest request = new LoginRequest("remember@example.com", "Password123!", true);

		MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andReturn();
		Cookie originalCookie = loginResult.getResponse().getCookie(RefreshCookieUtil.COOKIE_NAME);

		// First refresh: legitimate, rotates the token.
		mockMvc.perform(post("/api/auth/refresh").cookie(originalCookie))
				.andExpect(status().isOk());

		// Second refresh with the now-revoked original cookie: treated as reuse/theft.
		mockMvc.perform(post("/api/auth/refresh").cookie(originalCookie))
				.andExpect(status().isUnauthorized());

		// The whole family is dead now - even a token that would otherwise still be valid
		// is gone.
		assertThat(refreshTokenRepository.findAll())
				.allSatisfy(token -> assertThat(token.isRevoked()).isTrue());
	}

	@Test
	void refreshWithoutCookieIsRejected() throws Exception {
		mockMvc.perform(post("/api/auth/refresh"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void logoutRevokesTheRefreshTokenAndClearsTheCookie() throws Exception {
		LoginRequest request = new LoginRequest("remember@example.com", "Password123!", true);

		MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andReturn();
		Cookie refreshCookie = loginResult.getResponse().getCookie(RefreshCookieUtil.COOKIE_NAME);

		mockMvc.perform(post("/api/auth/logout").cookie(refreshCookie))
				.andExpect(status().isNoContent())
				.andExpect(cookie().maxAge(RefreshCookieUtil.COOKIE_NAME, 0));

		mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void logoutWithNoCookieIsANoOp() throws Exception {
		mockMvc.perform(post("/api/auth/logout"))
				.andExpect(status().isNoContent());
	}
}
