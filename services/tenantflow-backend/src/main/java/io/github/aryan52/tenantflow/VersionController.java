package io.github.aryan52.tenantflow;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exists purely to answer, unambiguously, "is this container actually running the code
 * I think it's running?" - bump BUILD_MARKER any time you need to prove a rebuild
 * actually took effect. Public: curl http://localhost:3000/api/version (through the
 * nginx proxy) or http://localhost:8081/api/version (hitting the backend directly).
 */
@RestController
public class VersionController {

	private static final String BUILD_MARKER = "phase-c-debug-3-password-change-hardening";

	public record VersionResponse(String build) {
	}

	@GetMapping("/api/version")
	public VersionResponse version() {
		return new VersionResponse(BUILD_MARKER);
	}
}
