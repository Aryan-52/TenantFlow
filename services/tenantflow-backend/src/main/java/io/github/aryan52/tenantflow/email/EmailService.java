package io.github.aryan52.tenantflow.email;

/**
 * Integration point for all outbound transactional email. Auth code depends only on
 * this interface, never on a specific provider - swap LoggingEmailService for a real
 * implementation (Resend, Brevo, SES, ...) behind this same contract when one is wired
 * up, without touching PasswordResetService or anything else that sends mail.
 */
public interface EmailService {

	void sendPasswordResetEmail(String toEmail, String resetUrl);
}
