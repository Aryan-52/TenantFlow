package io.github.aryan52.tenantflow.config;

import io.github.aryan52.tenantflow.email.EmailService;
import io.github.aryan52.tenantflow.email.LoggingEmailService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the fallback EmailService implementation.
 *
 * <p>Using @ConditionalOnMissingBean inside a @Configuration @Bean method is the only
 * reliable way to make a fallback bean conditional in Spring Boot. Using the annotation
 * directly on a component-scanned @Service bean does NOT work - the condition is
 * evaluated before the component scan is complete, so the "missing bean" check cannot
 * see other EmailService implementations that may also be provided through scanning.
 *
 * <p>To swap in a real email provider (Resend, Brevo, SES, …), define any @Bean or
 * @Service of type EmailService. This LoggingEmailService will not be registered.
 */
@Configuration
public class EmailConfig {

	@Bean
	@ConditionalOnMissingBean(EmailService.class)
	public EmailService emailService() {
		return new LoggingEmailService();
	}
}
