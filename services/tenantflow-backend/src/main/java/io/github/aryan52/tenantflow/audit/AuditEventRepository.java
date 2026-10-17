package io.github.aryan52.tenantflow.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
	Page<AuditEvent> findByTenantId(UUID tenantId, Pageable pageable);

	List<AuditEvent> findByAction(AuditAction action);
}
