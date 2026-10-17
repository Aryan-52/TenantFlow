package io.github.aryan52.tenantflow.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "idempotency_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class IdempotencyRecord {

	@Embeddable
	@Getter
	@EqualsAndHashCode
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	@AllArgsConstructor
	public static class Id implements Serializable {
		@Column(name = "user_id", nullable = false, columnDefinition = "uuid")
		private UUID userId;
		@Column(name = "scope", nullable = false, length = 100)
		private String scope;
		@Column(name = "idempotency_key", nullable = false, length = 100)
		private String key;
	}

	@EmbeddedId
	private Id id;

	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;

	@Setter
	@Column(name = "resource_id", columnDefinition = "uuid")
	private UUID resourceId;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}
