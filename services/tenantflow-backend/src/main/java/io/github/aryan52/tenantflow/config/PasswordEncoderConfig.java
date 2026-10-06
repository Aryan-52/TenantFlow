package io.github.aryan52.tenantflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Isolated configuration for the PasswordEncoder bean.
 *
 * Keeping this bean in a dedicated class (separate from SecurityConfig) breaks the
 * otherwise-circular dependency:
 *
 *   SecurityConfig → GoogleUserProvisioningService → PasswordEncoder → [back to SecurityConfig]
 *
 * Because GoogleUserProvisioningService now receives PasswordEncoder from this class,
 * which has no dependency on SecurityConfig at all, Spring can satisfy the graph in a
 * straightforward, non-circular order.
 */
@Configuration
public class PasswordEncoderConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
