package io.github.aryan52.tenantflow.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Default EmailService: logs the message instead of sending it. This is a deliberate
 * placeholder - no real email provider is wired up yet. Define a @Bean implementing
 * EmailService (e.g. backed by Resend/Brevo/SES) to replace this in any environment
 * that needs real delivery; it will automatically take priority over this one.
 */
@Service
@ConditionalOnMissingBean(EmailService.class)
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
