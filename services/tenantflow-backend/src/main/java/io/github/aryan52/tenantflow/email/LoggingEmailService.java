package io.github.aryan52.tenantflow.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default EmailService: logs the message instead of sending it. This is a deliberate
 * placeholder - no real email provider is wired up yet. Expose a @Bean implementing
 * EmailService (e.g. backed by Resend/Brevo/SES) to replace this - it will automatically
 * take priority because EmailConfig registers this one with @ConditionalOnMissingBean.
 *
 * <p>Note: intentionally NOT annotated with @Service. Bean registration is handled by
 * {@link io.github.aryan52.tenantflow.config.EmailConfig}, because @ConditionalOnMissingBean
 * only works correctly inside a @Configuration @Bean method - not on component-scanned beans.
 */
public class LoggingEmailService implements EmailService {

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

	@Override
	public void sendPasswordResetEmail(String toEmail, String resetUrl) {
		log.warn(
				"No EmailService provider is configured - password reset email NOT actually sent. "
						+ "Recipient: {}. In a real environment this link would have been emailed instead of logged: {}",
				toEmail, resetUrl
		);
	}
}
