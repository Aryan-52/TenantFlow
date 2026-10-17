package io.github.aryan52.tenantflow.ratelimit;

import io.github.aryan52.tenantflow.audit.AuditAction;
import io.github.aryan52.tenantflow.audit.AuditEvent.Outcome;
import io.github.aryan52.tenantflow.audit.AuditService;
import io.github.aryan52.tenantflow.audit.AuditService.Entry;
import io.github.aryan52.tenantflow.error.ApiErrorResponse;
import io.github.aryan52.tenantflow.security.CurrentUser;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import tools.jackson.databind.json.JsonMapper;

/**
 * Applies {@link RateLimitProperties} rules to abuse-prone endpoints. Runs after JWT
 * authentication so that authenticated rules can be counted per user instead of per IP.
 */
public class RateLimitFilter extends OncePerRequestFilter {

	enum KeyBy { IP, USER }

	record Route(String rule, HttpMethod method, PathPattern pattern, KeyBy keyBy) {
	}

	private static final PathPatternParser PARSER = new PathPatternParser();

	static final List<Route> ROUTES = List.of(
			new Route("login", HttpMethod.POST, PARSER.parse("/api/auth/login"), KeyBy.IP),
			new Route("register", HttpMethod.POST, PARSER.parse("/api/auth/register"), KeyBy.IP),
			new Route("forgot-password", HttpMethod.POST, PARSER.parse("/api/auth/forgot-password"), KeyBy.IP),
			new Route("reset-password", HttpMethod.POST, PARSER.parse("/api/auth/reset-password"), KeyBy.IP),
			new Route("refresh", HttpMethod.POST, PARSER.parse("/api/auth/refresh"), KeyBy.IP),
			new Route("upload", HttpMethod.POST, PARSER.parse("/api/tenants/*/projects/*/attachments"), KeyBy.USER),
			new Route("search", HttpMethod.GET, PARSER.parse("/api/search"), KeyBy.USER)
	);

	private final RateLimitProperties properties;
	private final RateLimiter limiter;
	private final AuditService auditService;
	private final MeterRegistry meterRegistry;
	private final JsonMapper jsonMapper;

	public RateLimitFilter(RateLimitProperties properties, RateLimiter limiter, AuditService auditService,
			MeterRegistry meterRegistry, JsonMapper jsonMapper) {
		this.properties = properties;
		this.limiter = limiter;
		this.auditService = auditService;
		this.meterRegistry = meterRegistry;
		this.jsonMapper = jsonMapper;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !properties.enabled();
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Route route = match(request);
		RateLimitProperties.Rule rule = route == null ? null : properties.rules().get(route.rule());
		if (rule == null) {
			chain.doFilter(request, response);
			return;
		}

		String subject = route.keyBy() == KeyBy.USER
				? CurrentUser.get().map(u -> "user:" + u.getId()).orElse("ip:" + request.getRemoteAddr())
				: "ip:" + request.getRemoteAddr();
		RateLimiter.Decision decision = limiter.tryConsume(route.rule() + ":" + subject, rule.limit(), rule.window());
		if (decision.allowed()) {
			chain.doFilter(request, response);
			return;
		}

		meterRegistry.counter("tenantflow.ratelimit.rejected", "rule", route.rule()).increment();
		// Audit only the first rejection per window, so an attack cannot flood the audit table.
		if (decision.count() == rule.limit() + 1L) {
			auditService.recordIndependently(Entry.of(AuditAction.RATE_LIMIT_EXCEEDED, Outcome.DENIED, null,
					"ENDPOINT", null, "rule=" + route.rule() + " subject=" + subject), null, null);
		}

		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSecs()));
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		jsonMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(HttpStatus.TOO_MANY_REQUESTS,
				"Too many requests. Please wait and try again.", request.getRequestURI()));
	}

	private static Route match(HttpServletRequest request) {
		var path = org.springframework.http.server.PathContainer.parsePath(request.getRequestURI());
		for (Route route : ROUTES) {
			if (route.method().matches(request.getMethod()) && route.pattern().matches(path)) {
				return route;
			}
		}
		return null;
	}
}
