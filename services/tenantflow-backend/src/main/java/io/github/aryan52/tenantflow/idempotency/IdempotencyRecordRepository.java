package io.github.aryan52.tenantflow.idempotency;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, IdempotencyRecord.Id> {

	@Modifying
	@Query("delete from IdempotencyRecord r where r.createdAt < :cutoff")
	int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
