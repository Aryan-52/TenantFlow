package io.github.aryan52.tenantflow.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class JwtPropertiesTests {

	private static final String SHORT_SECRET = "too-short-secret-value";

	@Configuration
	@EnableConfigurationProperties(JwtProperties.class)
	static class Config {
	}

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
			.withUserConfiguration(Config.class)
			.withPropertyValues("tenantflow.jwt.expiration-seconds=60");

	@Test
	void missingSecretNamesTheVariableToSet() {
		// What the real property resolves to when TENANTFLOW_JWT_SECRET is unset: ${TENANTFLOW_JWT_SECRET:} -> "".
		runner.withPropertyValues("tenantflow.jwt.secret=${TENANTFLOW_JWT_SECRET_NOT_SET_IN_TESTS:}")
				.run(context -> assertThat(context).getFailure()
						.rootCause()
						.hasMessageStartingWith("TENANTFLOW_JWT_SECRET is not set"));
	}

	@Test
	void tooShortSecretIsRejectedWithoutRevealingIt() {
		runner.withPropertyValues("tenantflow.jwt.secret=" + SHORT_SECRET)
				.run(context -> {
					assertThat(context).getFailure().rootCause().hasMessageContaining("TENANTFLOW_JWT_SECRET is too short");
					for (Throwable t = context.getStartupFailure(); t != null; t = t.getCause()) {
						assertThat(String.valueOf(t.getMessage())).doesNotContain(SHORT_SECRET);
					}
				});
	}

	@Test
	void validSecretStartsNormally() {
		runner.withPropertyValues("tenantflow.jwt.secret=" + "x".repeat(JwtProperties.MIN_SECRET_LENGTH))
				.run(context -> assertThat(context).hasNotFailed().hasSingleBean(JwtProperties.class));
	}

	@Test
	void constructorRulesMatchTheDocumentedMinimum() {
		assertThatThrownBy(() -> new JwtProperties("x".repeat(JwtProperties.MIN_SECRET_LENGTH - 1), 60))
				.hasMessageNotContaining("x".repeat(JwtProperties.MIN_SECRET_LENGTH - 1));
	}
}
