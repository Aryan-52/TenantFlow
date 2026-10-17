package io.github.aryan52.tenantflow.config;

import io.github.aryan52.tenantflow.oauth.GoogleClientRegistrationFactory;
import io.github.aryan52.tenantflow.oauth.GoogleUserProvisioningService;
import io.github.aryan52.tenantflow.oauth.OAuth2LoginFailureHandler;
import io.github.aryan52.tenantflow.oauth.OAuth2LoginSuccessHandler;
import io.github.aryan52.tenantflow.ratelimit.RateLimitFilter;
import io.github.aryan52.tenantflow.security.JwtAuthenticationFilter;
import io.github.aryan52.tenantflow.security.RestAccessDeniedHandler;
import io.github.aryan52.tenantflow.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, RefreshTokenProperties.class,
		PasswordResetProperties.class, CookieProperties.class, GoogleOAuthProperties.class})
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final RateLimitFilter rateLimitFilter;
	private final RestAuthenticationEntryPoint authenticationEntryPoint;
	private final RestAccessDeniedHandler accessDeniedHandler;
	private final CorsProperties corsProperties;
	private final GoogleOAuthProperties googleOAuthProperties;
	private final GoogleUserProvisioningService googleUserProvisioningService;
	private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
	private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// Defaults kept: X-Content-Type-Options: nosniff, X-Frame-Options: DENY,
				// Cache-Control: no-store, and HSTS on HTTPS requests. Added on top:
				.headers(headers -> headers
						.referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
						.permissionsPolicyHeader(permissions -> permissions.policy("camera=(), microphone=(), geolocation=(), payment=()"))
						// API responses are data, never documents: forbid every resource type and
						// framing. Scoped to /api/** so the (dev-only) API docs UI can still load.
						.addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
								PathPatternRequestMatcher.withDefaults().matcher("/api/**"),
								new StaticHeadersWriter("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")))
				)
				.exceptionHandling(exceptionHandling -> exceptionHandling
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler)
				)
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
								"/api/auth/refresh", "/api/auth/logout",
								"/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/auth/oauth-providers", "/oauth2/authorization/google", "/api/version").permitAll()
						.requestMatchers("/api/**").authenticated()
						.anyRequest().permitAll()
				)
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				// After JWT so per-user limits know the user; before any controller work.
				.addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);

		// Only registered when both GOOGLE_OAUTH_CLIENT_ID and GOOGLE_OAUTH_CLIENT_SECRET are
		// configured. When they are not, .oauth2Login(...) is never added to the filter chain
		// at all, so /oauth2/authorization/google simply doesn't exist (404) rather than the
		// application failing to start or presenting a broken/fake login button.
		if (googleOAuthProperties.isConfigured()) {
			http.oauth2Login(oauth2 -> oauth2
					.clientRegistrationRepository(GoogleClientRegistrationFactory.build(googleOAuthProperties))
					.userInfoEndpoint(userInfo -> userInfo.oidcUserService(googleUserProvisioningService))
					.successHandler(oAuth2LoginSuccessHandler)
					.failureHandler(oAuth2LoginFailureHandler)
			);
		}

		return http.build();
	}

	@Bean
	public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
		org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
		configuration.setAllowedOrigins(corsProperties.allowedOrigins());
		configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		// Only what the frontend actually sends, instead of "*".
		configuration.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type", "Accept",
				"X-Request-Id", "Idempotency-Key"));
		configuration.setExposedHeaders(java.util.List.of("X-Request-Id", "Retry-After", "Content-Disposition"));
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(3600L);
		org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

}
