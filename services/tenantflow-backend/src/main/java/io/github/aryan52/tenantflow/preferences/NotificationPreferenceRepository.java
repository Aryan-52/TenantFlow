package io.github.aryan52.tenantflow.preferences;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

	List<NotificationPreference> findByUserIdIn(Collection<UUID> userIds);
}
