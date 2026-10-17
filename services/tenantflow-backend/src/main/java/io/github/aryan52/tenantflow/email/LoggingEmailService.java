package io.github.aryan52.tenantflow.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default EmailService: logs that a message would have been sent instead of sending it.
 * This is a deliberate placeholder - no real email provider is wired up yet. Expose a @Bean
 * implementing EmailService (e.g. backed by Resend/Brevo/SES) to replace this - it will
 * automatically take priority because EmailConfig registers this one with
 * {@code @ConditionalOnMissingBean}.
 *
 * <p>Password reset links are bearer credentials, so they are NOT logged unless
 * {@code tenantflow.email.log-reset-links=true} (local development only), and the
 * recipient address is masked.
 *
 * <p>Note: intentionally NOT annotated with @Service. Bean registration is handled by
 * {@link io.github.aryan52.tenantflow.config.EmailConfig}, because @ConditionalOnMissingBean
 * only works correctly inside a @Configuration @Bean method - not on component-scanned beans.
 */
public class LoggingEmailService implements EmailService {

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

	private final boolean logResetLinks;

	public LoggingEmailService(boolean logResetLinks) {
		this.logResetLinks = logResetLinks;
	}

	@Override
	public void sendPasswordResetEmail(String toEmail, String resetUrl) {
		if (logResetLinks) {
			log.warn("No EmailService provider is configured. LOCAL DEVELOPMENT ONLY - password reset link for {}: {}",
					mask(toEmail), resetUrl);
			return;
		}
		log.warn("No EmailService provider is configured - password reset email for {} was NOT sent. "
				+ "Configure an EmailService, or set TENANTFLOW_EMAIL_LOG_RESET_LINKS=true for local development.",
				mask(toEmail));
	}

	/** "alice@example.com" -> "a***@example.com". */
	static String mask(String email) {
		if (email == null) {
			return null;
		}
		int at = email.indexOf('@');
		if (at <= 0) {
			return "***";
		}
		return email.charAt(0) + "***" + email.substring(at);
	}
}
