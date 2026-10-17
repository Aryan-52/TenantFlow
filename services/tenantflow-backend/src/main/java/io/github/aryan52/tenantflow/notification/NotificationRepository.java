package io.github.aryan52.tenantflow.notification;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every read is scoped to the recipient AND to workspaces the recipient still belongs to
 * (or to workspace-less notices), so leaving a workspace hides its notifications at once.
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	String VISIBLE = "n.recipientId = :userId and (n.tenantId is null or n.tenantId in "
			+ "(select m.tenant.id from Membership m where m.user.id = :userId))";

	@Query("select n from Notification n where " + VISIBLE + " and (:unreadOnly = false or n.readAt is null)")
	Page<Notification> findVisible(@Param("userId") UUID userId, @Param("unreadOnly") boolean unreadOnly, Pageable pageable);

	/** Same as findVisible, restricted to some notification types (inbox categories). */
	@Query("select n from Notification n where " + VISIBLE + " and (:unreadOnly = false or n.readAt is null) and n.type in :types")
	Page<Notification> findVisibleOfTypes(@Param("userId") UUID userId, @Param("unreadOnly") boolean unreadOnly,
			@Param("types") java.util.Collection<String> types, Pageable pageable);

	@Query("select count(n) from Notification n where " + VISIBLE + " and n.readAt is null")
	long countUnread(@Param("userId") UUID userId);

	@Query("select n from Notification n where n.id = :id and " + VISIBLE)
	Optional<Notification> findVisibleById(@Param("id") UUID id, @Param("userId") UUID userId);

	@Modifying
	@Query("update Notification n set n.readAt = :now where " + VISIBLE + " and n.readAt is null")
	int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);

	boolean existsByRecipientIdAndDedupeKey(UUID recipientId, String dedupeKey);

	@Modifying
	@Query("delete from Notification n where n.tenantId = :tenantId")
	void deleteAllByTenantId(@Param("tenantId") UUID tenantId);
}
