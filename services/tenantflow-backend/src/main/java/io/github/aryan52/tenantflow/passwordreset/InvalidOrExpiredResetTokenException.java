package io.github.aryan52.tenantflow.passwordreset;

/** Deliberately used for every reason a reset token can fail (not found, expired,
 * already used) - the message is generic on purpose so a caller learns nothing about
 * *why* a token didn't work. */
public class InvalidOrExpiredResetTokenException extends RuntimeException {

	public InvalidOrExpiredResetTokenException() {
		super("This password reset link is invalid or has expired.");
	}
}
