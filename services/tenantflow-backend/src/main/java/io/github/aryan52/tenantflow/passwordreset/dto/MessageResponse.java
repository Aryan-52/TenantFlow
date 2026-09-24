package io.github.aryan52.tenantflow.passwordreset.dto;

/** Generic single-message response body, used where the response must say the same
 * thing regardless of outcome (e.g. "forgot password" never reveals whether the email
 * exists). */
public record MessageResponse(String message) {
}
