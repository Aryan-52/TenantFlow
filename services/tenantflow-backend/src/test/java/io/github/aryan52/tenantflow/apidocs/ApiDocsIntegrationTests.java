package io.github.aryan52.tenantflow.apidocs;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

class ApiDocsIntegrationTests {

	private static final String DB = "spring.datasource.url=jdbc:h2:mem:tenantflow_apidocs;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

	/** The default configuration (as in production): nothing is published. */
	@Nested
	@SpringBootTest(properties = {
			DB,
			"spring.datasource.driver-class-name=org.h2.Driver",
			"spring.datasource.username=sa",
			"spring.datasource.password=",
			"spring.jpa.hibernate.ddl-auto=create-drop",
			"spring.flyway.enabled=false",
			"tenantflow.jwt.secret=test-apidocs-secret-test-apidocs-secret-test-apidocs-secret"
	})
	@AutoConfigureMockMvc
	class DisabledByDefault {

		@Autowired
		private MockMvc mockMvc;

		@Test
		void apiDocsAndSwaggerUiAreNotServed() throws Exception {
			mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
			mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
			mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
		}
	}

	@Nested
	@SpringBootTest(properties = {
			DB,
			"spring.datasource.driver-class-name=org.h2.Driver",
			"spring.datasource.username=sa",
			"spring.datasource.password=",
			"spring.jpa.hibernate.ddl-auto=create-drop",
			"spring.flyway.enabled=false",
			"tenantflow.jwt.secret=test-apidocs-secret-test-apidocs-secret-test-apidocs-secret",
			"springdoc.api-docs.enabled=true",
			"springdoc.swagger-ui.enabled=true"
	})
	@AutoConfigureMockMvc
	class EnabledForLocalDevelopment {

		@Autowired
		private MockMvc mockMvc;

		@Test
		void documentDescribesSecurityErrorsPagingAndRequestIds() throws Exception {
			mockMvc.perform(get("/v3/api-docs"))
					.andExpect(status().isOk())
					.andExpect(header().exists("X-Request-Id"))
					.andExpect(jsonPath("$.info.title").value("TenantFlow API"))
					.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
					.andExpect(jsonPath("$.security[0]", hasKey("bearerAuth")))
					.andExpect(jsonPath("$.components.schemas", hasKey("ApiErrorResponse")))
					.andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties", hasKey("requestId")))
					.andExpect(jsonPath("$.components.headers", hasKey("X-Request-Id")))
					// Public endpoints opt out of the global bearer requirement.
					.andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty())
					.andExpect(jsonPath("$.paths['/api/auth/login'].post.responses", hasKey("429")))
					.andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['429'].headers", hasKey("Retry-After")))
					// Tenant-scoped endpoints document 401/403/404 and paging parameters.
					.andExpect(jsonPath("$.paths['/api/tenants/{tenantId}/tasks'].get.responses", hasKey("403")))
					.andExpect(jsonPath("$.paths['/api/tenants/{tenantId}/tasks'].get.responses", hasKey("401")))
					.andExpect(jsonPath("$.paths['/api/tenants/{tenantId}/tasks'].get.parameters[*].name", hasItem("size")))
					.andExpect(jsonPath("$.paths['/api/tenants/{tenantId}/tasks'].get.responses['200'].headers", hasKey("X-Request-Id")))
					// Uploads are documented as multipart.
					.andExpect(jsonPath("$.paths['/api/tenants/{tenantId}/projects/{projectId}/attachments'].post.requestBody.content",
							hasKey("multipart/form-data")))
					// Only the API is documented (no actuator or OAuth internals).
					.andExpect(jsonPath("$.paths", org.hamcrest.Matchers.not(hasKey("/livez"))));
		}

		@Test
		void swaggerUiIsServed() throws Exception {
			mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
		}
	}
}
