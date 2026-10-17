package io.github.aryan52.tenantflow.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Production chain with the Vercel proxy: browser -> Vercel -> Render's load balancer -> app.
 * Vercel puts the browser's address in X-Forwarded-For (overwriting anything the browser sent)
 * and the load balancer appends Vercel's own public address. These tests send exactly that
 * header through a real Tomcat (the request itself arrives from 127.0.0.1, an internal
 * proxy like Render's balancer) and check whose address the per-IP sign-in limit counts.
 */
class ProxiedClientIpIntegrationTests {

	/** A public address standing in for Vercel's egress proxy. */
	private static final String VERCEL = "76.76.21.21";


	private static int login(int port, String browserIp) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
				.header("Content-Type", "application/json")
				.header("X-Forwarded-For", browserIp + ", " + VERCEL)
				.POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"nobody@example.com\",\"password\":\"wrong-password\"}"))
				.build();
		return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
			"spring.datasource.url=jdbc:h2:mem:tenantflow_proxy_ip_default;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
			"spring.datasource.driver-class-name=org.h2.Driver",
			"spring.datasource.username=sa",
			"spring.datasource.password=",
			"spring.jpa.hibernate.ddl-auto=create-drop",
			"spring.flyway.enabled=false",
			"tenantflow.jwt.secret=proxy-ip-test-secret-proxy-ip-test-secret-0001",
			"management.server.port=",
			"tenantflow.rate-limit.enabled=true",
			"tenantflow.rate-limit.store=memory",
			"tenantflow.rate-limit.rules.login.limit=2",
			"tenantflow.rate-limit.rules.login.window=1m"
	})
	class Default extends Base {
		@Test
		void withoutTrustingTheProxyEveryUserSharesVercelsAddress() throws Exception {
			assertThat(login(port, "198.51.100.1")).isNotEqualTo(429);
			assertThat(login(port, "198.51.100.2")).isNotEqualTo(429);
			// A third, different user is already limited: the bucket belongs to Vercel's address.
			assertThat(login(port, "198.51.100.3")).isEqualTo(429);
		}
	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
			"spring.datasource.url=jdbc:h2:mem:tenantflow_proxy_ip_trusted;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
			"spring.datasource.driver-class-name=org.h2.Driver",
			"spring.datasource.username=sa",
			"spring.datasource.password=",
			"spring.jpa.hibernate.ddl-auto=create-drop",
			"spring.flyway.enabled=false",
			"tenantflow.jwt.secret=proxy-ip-test-secret-proxy-ip-test-secret-0001",
			"management.server.port=",
			"tenantflow.rate-limit.enabled=true",
			"tenantflow.rate-limit.store=memory",
			"tenantflow.rate-limit.rules.login.limit=2",
			"tenantflow.rate-limit.rules.login.window=1m",
			// Render: SERVER_TOMCAT_REMOTEIP_TRUSTED_PROXIES=.* (only when all traffic comes through the Vercel proxy).
			"server.tomcat.remoteip.trusted-proxies=.*"
	})
	class TrustingTheProxy extends Base {
		@Test
		void eachUserGetsTheirOwnLimit() throws Exception {
			assertThat(login(port, "203.0.113.1")).isNotEqualTo(429);
			assertThat(login(port, "203.0.113.1")).isNotEqualTo(429);
			assertThat(login(port, "203.0.113.1")).isEqualTo(429);
			// Another user is unaffected by the first one's failures.
			assertThat(login(port, "203.0.113.2")).isNotEqualTo(429);
		}
	}

	abstract static class Base {
		@LocalServerPort
		int port;
	}
}
