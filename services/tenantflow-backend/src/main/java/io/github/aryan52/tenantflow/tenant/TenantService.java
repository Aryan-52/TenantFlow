package io.github.aryan52.tenantflow.tenant;

import io.github.aryan52.tenantflow.activity.ActivityEventRepository;
import io.github.aryan52.tenantflow.attachment.AttachmentService;
import io.github.aryan52.tenantflow.audit.AuditAction;
import io.github.aryan52.tenantflow.notification.NotificationRepository;
import io.github.aryan52.tenantflow.audit.AuditService;
import io.github.aryan52.tenantflow.audit.AuditService.Entry;
import io.github.aryan52.tenantflow.cache.TenantCacheInvalidator;
import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.Role;
import io.github.aryan52.tenantflow.entity.Tenant;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.error.DuplicateTenantException;
import io.github.aryan52.tenantflow.error.ResourceNotFoundException;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.TenantRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.tenant.dto.CreateTenantRequest;
import io.github.aryan52.tenantflow.tenant.dto.TenantResponse;
import io.github.aryan52.tenantflow.tenant.dto.UpdateTenantRequest;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantService {

	private final TenantRepository tenantRepository;
	private final MembershipRepository membershipRepository;
	private final UserRepository userRepository;
	private final AuditService auditService;
	private final TenantCacheInvalidator cacheInvalidator;
	private final ActivityEventRepository activityEventRepository;
	private final NotificationRepository notificationRepository;
	private final io.github.aryan52.tenantflow.automation.AutomationRunRepository automationRunRepository;
	private final io.github.aryan52.tenantflow.automation.AutomationRuleRepository automationRuleRepository;
	private final io.github.aryan52.tenantflow.planning.TaskDependencyRepository dependencyRepository;
	private final AttachmentService attachmentService;

	@Transactional
	public TenantResponse createTenant(CreateTenantRequest request, UUID userId) {
		if (tenantRepository.existsBySlug(request.slug())) {
			throw new DuplicateTenantException(request.slug());
		}

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User"));

		Tenant tenant = Tenant.builder()
				.name(request.name().trim())
				.slug(request.slug().trim())
				.build();

		try {
			tenant = tenantRepository.save(tenant);
			Membership membership = Membership.builder()
					.tenant(tenant)
					.user(user)
					.role(Role.OWNER)
					.build();
			membershipRepository.saveAndFlush(membership);
			auditService.record(Entry.success(AuditAction.WORKSPACE_CREATED, tenant.getId(), "WORKSPACE", tenant.getId(),
					"slug=" + tenant.getSlug()));

			return toResponse(tenant, Role.OWNER);
		} catch (DataIntegrityViolationException ex) {
			throw new DuplicateTenantException(request.slug());
		}
	}

	@Transactional(readOnly = true)
	public List<TenantResponse> getUserTenants(UUID userId) {
		return membershipRepository.findByUser_Id(userId).stream()
				.map(membership -> toResponse(membership.getTenant(), membership.getRole()))
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public TenantResponse getTenant(UUID tenantId, UUID userId) {
		Tenant tenant = tenantRepository.findById(tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Tenant"));

		Role myRole = membershipRepository.findByUser_IdAndTenant_Id(userId, tenantId)
				.map(Membership::getRole)
				.orElse(null);

		return toResponse(tenant, myRole);
	}

	@Transactional
	public TenantResponse updateTenant(UUID tenantId, UpdateTenantRequest request, UUID userId) {
		Tenant tenant = tenantRepository.findById(tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Tenant"));

		String previousName = tenant.getName();
		tenant.setName(request.name().trim());
		tenant = tenantRepository.save(tenant);
		if (!previousName.equals(tenant.getName())) {
			auditService.record(Entry.success(AuditAction.WORKSPACE_UPDATED, tenantId, "WORKSPACE", tenantId, "name changed"));
		}

		Role myRole = membershipRepository.findByUser_IdAndTenant_Id(userId, tenantId)
				.map(Membership::getRole)
				.orElse(null);

		return toResponse(tenant, myRole);
	}

	@Transactional
	public void deleteTenant(UUID tenantId) {
		Tenant tenant = tenantRepository.findById(tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Tenant"));
		
		List<Membership> memberships = membershipRepository.findByTenant_Id(tenantId);
		// Explicit (not only ON DELETE CASCADE) so behaviour is the same on every database.
		notificationRepository.deleteAllByTenantId(tenantId);
		attachmentService.deleteAllForTenant(tenantId);
		activityEventRepository.deleteAllByTenantId(tenantId);
		automationRunRepository.deleteAllByTenantId(tenantId);
		automationRuleRepository.deleteAllByTenantId(tenantId);
		dependencyRepository.deleteAll(dependencyRepository.findByTenantId(tenantId));
		membershipRepository.deleteAll(memberships);
		tenantRepository.delete(tenant);
		cacheInvalidator.invalidate(tenantId);
		// The audit row has no foreign key to the tenant, so it outlives the deletion.
		auditService.record(Entry.success(AuditAction.WORKSPACE_DELETED, tenantId, "WORKSPACE", tenantId,
				"slug=" + tenant.getSlug() + " members=" + memberships.size()));
	}

	private TenantResponse toResponse(Tenant tenant, Role myRole) {
		return new TenantResponse(
				tenant.getId(),
				tenant.getName(),
				tenant.getSlug(),
				tenant.getCreatedAt(),
				tenant.getUpdatedAt(),
				myRole
		);
	}
}
