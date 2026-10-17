package io.github.aryan52.tenantflow.idempotency;

import io.github.aryan52.tenantflow.error.ConflictException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Idempotent creates. Within the caller's transaction the key is claimed first (insert),
 * so a concurrent duplicate blocks on the primary key until the first request finishes;
 * a later replay finds the completed record and gets the original resource back. If the
 * create fails, the claim rolls back with it and the client can safely retry.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

	private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
	private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,100}");
	static final Duration RETENTION = Duration.ofHours(24);

	private final IdempotencyRecordRepository repository;
	private final JsonMapper jsonMapper;

	/**
	 * @param key     the client's Idempotency-Key, or null to just run {@code create}
	 * @param request the request body; replaying a key with a different body is rejected
	 * @param create  performs the create and returns the new resource id
	 * @return the id of the resource created by this call or by the original call
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public UUID run(UUID userId, String scope, String key, Object request, Supplier<UUID> create) {
		if (key == null) {
			return create.get();
		}
		if (!VALID_KEY.matcher(key).matches()) {
			throw new IllegalArgumentException("Idempotency-Key must be 8-100 characters of letters, digits, '.', '_', ':' or '-'");
		}
		String hash = hash(request);
		IdempotencyRecord.Id id = new IdempotencyRecord.Id(userId, scope, key);

		Optional<IdempotencyRecord> existing = repository.findById(id);
		if (existing.isPresent()) {
			return replay(existing.get(), hash);
		}

		IdempotencyRecord claim;
		try {
			// The id is client-assigned, so save() merges and returns the managed instance;
			// later changes must go to that instance, not the argument.
			claim = repository.saveAndFlush(new IdempotencyRecord(id, hash, null, null));
		} catch (DataIntegrityViolationException ex) {
			// Lost the race against a concurrent request with the same key.
			throw new ConflictException("A request with this Idempotency-Key is already being processed");
		}
		UUID resourceId = create.get();
		claim.setResourceId(resourceId);
		return resourceId;
	}

	private static UUID replay(IdempotencyRecord record, String hash) {
		if (!record.getRequestHash().equals(hash)) {
			throw new ConflictException("This Idempotency-Key was already used with a different request");
		}
		if (record.getResourceId() == null) {
			throw new ConflictException("A request with this Idempotency-Key is already being processed");
		}
		return record.getResourceId();
	}

	private String hash(Object request) {
		try {
			byte[] json = jsonMapper.writeValueAsBytes(request);
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Hourly purge of keys past the replay window. */
	@Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
	@Transactional
	public void purgeExpired() {
		int deleted = repository.deleteOlderThan(Instant.now().minus(RETENTION));
		if (deleted > 0) {
			log.info("Purged {} expired idempotency keys", deleted);
		}
	}
}
