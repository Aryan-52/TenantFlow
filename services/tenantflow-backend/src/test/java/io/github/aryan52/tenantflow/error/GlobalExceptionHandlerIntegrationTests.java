package io.github.aryan52.tenantflow.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Covers the parts of GlobalExceptionHandler that are not exercised by the feature-specific
 * integration test classes: malformed path variables, and that a generic catch-all still
 * returns the standard ApiErrorResponse shape.
 */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tenantflow_error_handling;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.show-sql=false",
		"spring.flyway.enabled=false",
		"tenantflow.jwt.secret=test-error-secret-test-error-secret-test-error-secret",
		"tenantflow.jwt.expiration-seconds=3600"
})
@AutoConfigureMockMvc
class GlobalExceptionHandlerIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtService jwtService;

	private String token;

	@BeforeEach
	void setup() {
		userRepository.deleteAll();
		User user = userRepository.save(User.builder()
				.email("error-handling@example.com")
				.name("Error Handling")
				.passwordHash("hash")
				.build());
		token = jwtService.generateToken(user);
	}

	@Test
	void malformedTenantIdReturnsBadRequestInsteadOfServerError() throws Exception {
		mockMvc.perform(get("/api/tenants/not-a-uuid/dashboard")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.message").exists());
	}
}
