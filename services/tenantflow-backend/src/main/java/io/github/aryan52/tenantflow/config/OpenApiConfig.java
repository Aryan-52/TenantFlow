package io.github.aryan52.tenantflow.config;

import io.github.aryan52.tenantflow.error.ApiErrorResponse;
import io.github.aryan52.tenantflow.web.RequestCorrelationFilter;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document at {@code /v3/api-docs} and Swagger UI at {@code /swagger-ui.html}.
 *
 * <p>Off unless {@code TENANTFLOW_API_DOCS_ENABLED=true} (see application.properties), so a
 * production deployment never publishes it by accident. The document describes the API's
 * cross-cutting contracts once (auth, roles, paging, errors, request ids, uploads) and
 * attaches the shared error responses and headers to every operation.
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfig {

	static final String BEARER = "bearerAuth";
	private static final String ERROR_SCHEMA = "ApiErrorResponse";
	private static final String REQUEST_ID_HEADER = RequestCorrelationFilter.HEADER;

	/** Endpoints reachable without a token (mirrors SecurityConfig). */
	private static final Set<String> PUBLIC_PATHS = Set.of(
			"/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
			"/api/auth/forgot-password", "/api/auth/reset-password", "/api/auth/oauth-providers", "/api/version");

	private static final String DESCRIPTION = """
			Multi-tenant project and task management API.

			**Authentication.** `POST /api/auth/login` returns a short-lived JWT access token; send it as \
			`Authorization: Bearer <token>`. With "remember me" the server also sets an HttpOnly refresh-token \
			cookie; `POST /api/auth/refresh` exchanges it for a new access token.

			**Workspaces and permissions.** Everything under `/api/tenants/{tenantId}` requires membership of that \
			workspace. Roles are hierarchical: `OWNER` > `ADMIN` > `MEMBER`. Members read and edit projects and \
			tasks; admins also delete them, manage members and read the audit log; only owners manage owners and \
			delete the workspace. A missing membership yields `403`; resources in another workspace are reported \
			as `404`, so their existence is never revealed.

			**Pagination.** Paged endpoints accept `page` (0-based, default 0) and `size` (1-100, default 20) and \
			return `{items, page, size, totalElements, totalPages, hasNext}`.

			**Errors.** Every error uses the `ApiErrorResponse` body. `fieldErrors` lists validation problems per \
			field. `409` signals an optimistic-locking conflict (send the `version` you read) or an idempotency-key \
			reuse; `429` carries a `Retry-After` header (seconds).

			**Request ids.** Every response carries `X-Request-Id`. Send your own (8-64 characters of \
			`[A-Za-z0-9._-]`) to correlate client and server logs; it is echoed back and appears in error bodies.

			**Idempotent creates.** `POST` on projects and tasks accepts an optional `Idempotency-Key` header; \
			retrying with the same key and body returns the original result instead of creating a duplicate; the same 			key with a different body, or while the first request is still running, gets `409`.

			**Uploads.** Attachments are `multipart/form-data` with a `file` part (max 10 MB by default). The \
			type must be on the allow-list and match the file's leading bytes, otherwise `400`; an oversized file 			gets `413`.
			""";

	@Bean
	public OpenAPI tenantflowOpenApi() {
		Components components = new Components()
				.addSecuritySchemes(BEARER, new SecurityScheme()
						.type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
						.description("Access token from POST /api/auth/login or /api/auth/refresh."))
				.addHeaders(REQUEST_ID_HEADER, new Header()
						.description("Correlation id of this request (echoed from the request when valid).")
						.schema(new StringSchema()))
				.addHeaders("Retry-After", new Header()
						.description("Seconds to wait before retrying.")
						.schema(new Schema<Integer>().type("integer")));

		return new OpenAPI()
				.info(new Info().title("TenantFlow API").version("v1").description(DESCRIPTION))
				.components(components)
				.addSecurityItem(new SecurityRequirement().addList(BEARER));
	}

	/** Shared responses, headers and parameter docs for every operation. */
	@Bean
	public OpenApiCustomizer tenantflowOperationDefaults() {
		return openApi -> {
			// Registered here, after springdoc has generated the controller schemas.
			ModelConverters.getInstance().read(ApiErrorResponse.class).forEach(openApi.getComponents()::addSchemas);
			openApi.getPaths().forEach((path, item) -> item.readOperations().forEach(op -> addDefaults(path, op)));
		};
	}

	private static void addDefaults(String path, Operation op) {
		boolean isPublic = PUBLIC_PATHS.contains(path);
		if (isPublic) {
			op.setSecurity(List.of()); // overrides the global bearer requirement
		}
		ApiResponses responses = op.getResponses();
		responses.values().forEach(r -> r.addHeaderObject(REQUEST_ID_HEADER, headerRef(REQUEST_ID_HEADER)));

		error(responses, "400", "Invalid request, parameters or body (see fieldErrors).");
		if (!isPublic) {
			error(responses, "401", "Missing, invalid or expired access token.");
			if (path.startsWith("/api/tenants/{tenantId}")) {
				error(responses, "403", "Not a member of the workspace, or the role is insufficient.");
				error(responses, "404", "Not found in this workspace.");
			}
		}
		ApiResponse tooMany = error(responses, "429", "Rate limit exceeded.");
		if (tooMany != null) {
			tooMany.addHeaderObject("Retry-After", headerRef("Retry-After"));
		}
		error(responses, "500", "Unexpected server error; quote the requestId when reporting it.");
		describeCommonParameters(op);
	}

	private static void describeCommonParameters(Operation op) {
		if (op.getParameters() == null) {
			return;
		}
		Map<String, String> docs = Map.of(
				"page", "0-based page number (default 0).",
				"size", "Page size, 1-100 (default 20).",
				"Idempotency-Key", "Optional client-generated key (8-100 characters of letters, digits, '.', '_', ':' or '-') that makes this create safe to retry.");
		op.getParameters().forEach(p -> {
			if (p.getDescription() == null && docs.containsKey(p.getName())) {
				p.setDescription(docs.get(p.getName()));
			}
		});
	}

	/** Adds a documented error response unless the operation already declares that status. */
	private static ApiResponse error(ApiResponses responses, String status, String description) {
		if (responses.containsKey(status)) {
			return null;
		}
		ApiResponse response = new ApiResponse()
				.description(description)
				.content(new Content().addMediaType("application/json",
						new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA))))
				.addHeaderObject(REQUEST_ID_HEADER, headerRef(REQUEST_ID_HEADER));
		responses.addApiResponse(status, response);
		return response;
	}

	private static Header headerRef(String name) {
		return new Header().$ref("#/components/headers/" + name);
	}
}
