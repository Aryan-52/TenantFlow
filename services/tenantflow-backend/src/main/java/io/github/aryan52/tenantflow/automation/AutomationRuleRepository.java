package io.github.aryan52.tenantflow.automation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationRuleRepository extends JpaRepository<AutomationRule, UUID> {

	List<AutomationRule> findByTenantId(UUID tenantId);

	Optional<AutomationRule> findByTenantIdAndRuleType(UUID tenantId, AutomationRuleType ruleType);

	boolean existsByTenantIdAndRuleTypeAndEnabledTrue(UUID tenantId, AutomationRuleType ruleType);

	List<AutomationRule> findByRuleTypeAndEnabledTrue(AutomationRuleType ruleType);

	void deleteAllByTenantId(UUID tenantId);
}
