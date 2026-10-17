package io.github.aryan52.tenantflow.error;

/** 429 with a Retry-After hint. */
public class RateLimitExceededException extends RuntimeException {

	private final long retryAfterSeconds;

	public RateLimitExceededException(long retryAfterSeconds) {
		super("Too many requests. Please wait and try again.");
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}
}
