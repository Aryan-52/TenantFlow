package io.github.aryan52.tenantflow.membership;

import io.github.aryan52.tenantflow.activity.ActivityService;
import io.github.aryan52.tenantflow.activity.ActivityService.Notify;
import io.github.aryan52.tenantflow.audit.AuditAction;
import io.github.aryan52.tenantflow.audit.AuditEvent.Outcome;
import io.github.aryan52.tenantflow.audit.AuditService;
import io.github.aryan52.tenantflow.audit.AuditService.Entry;
import io.github.aryan52.tenantflow.cache.TenantCacheInvalidator;
import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.Role;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import io.github.aryan52.tenantflow.entity.Tenant;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.error.DuplicateMembershipException;
import io.github.aryan52.tenantflow.error.ForbiddenOperationException;
import io.github.aryan52.tenantflow.error.ResourceNotFoundException;
import io.github.aryan52.tenantflow.membership.dto.AddMembershipRequest;
import io.github.aryan52.tenantflow.membership.dto.MembershipResponse;
import io.github.aryan52.tenantflow.membership.dto.UpdateMembershipRequest;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import io.github.aryan52.tenantflow.repository.TaskRepository;
import io.github.aryan52.tenantflow.repository.TenantRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workspace membership management. Controllers already restrict these operations to
 * ADMIN/OWNER; this service additionally enforces the role hierarchy documented on
 * {@link Role} so that an ADMIN cannot escalate themselves or anyone else to OWNER, nor
 * change or remove an OWNER.
 */
@Service
@RequiredArgsConstructor
public class MembershipService {

	private static final String TARGET = "MEMBERSHIP";

	private final MembershipRepository membershipRepository;
	private final TaskRepository taskRepository;
	private final TenantRepository tenantRepository;
	private final UserRepository userRepository;
	private final ProjectRepository projectRepository;
	private final AuditService auditService;
	private final TenantCacheInvalidator cacheInvalidator;
	private final ActivityService activityService;

	@Transactional(readOnly = true)
	public List<MembershipResponse> getMemberships(UUID tenantId) {
		if (!tenantRepository.existsById(tenantId)) {
			throw new ResourceNotFoundException("Tenant");
		}
		// One grouped query for everyone's workload (no per-member queries).
		Map<UUID, TaskRepository.AssigneeWorkload> workload = taskRepository
				.workloadByAssignee(tenantId, TaskStatus.DONE, LocalDate.now())
				.stream().collect(Collectors.toMap(TaskRepository.AssigneeWorkload::getUserId, w -> w));
		return membershipRepository.findByTenant_Id(tenantId).stream()
				.map(m -> {
					TaskRepository.AssigneeWorkload w = workload.get(m.getUser().getId());
					return MembershipResponse.from(m, w == null ? 0L : w.getOpen(), w == null ? 0L : w.getOverdue());
				})
				.collect(Collectors.toList());
	}

	@Transactional
	public MembershipResponse addMembership(UUID tenantId, AddMembershipRequest request) {
		Tenant tenant = tenantRepository.findById(tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Tenant"));
		Role actorRole = actorRole(tenantId);
		if (!actorRole.atLeast(request.role())) {
			throw deny(tenantId, null, "Cannot grant the " + request.role() + " role: it is above your own role");
		}

		User user = userRepository.findByEmail(request.email().toLowerCase().trim())
				.orElseThrow(() -> new ResourceNotFoundException("User with email " + request.email()));

		if (membershipRepository.existsByUser_IdAndTenant_Id(user.getId(), tenantId)) {
			throw new DuplicateMembershipException(user.getEmail());
		}

		Membership membership = Membership.builder()
				.tenant(tenant)
				.user(user)
				.role(request.role())
				.build();

		try {
			membership = membershipRepository.saveAndFlush(membership);
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateMembershipException(user.getEmail());
		}
		activityService.record(tenantId, null, null, "MEMBER_ADDED", "added " + user.getName() + " as " + roleLabel(request.role()),
				List.of(Notify.of(user.getId(), "MEMBER_ADDED", "You were added to " + tenant.getName(),
						"Role: " + roleLabel(request.role()), "/tenants/" + tenantId + "/dashboard",
						"membership:" + membership.getId() + ":added")));
		cacheInvalidator.invalidate(tenantId);
		auditService.record(Entry.success(AuditAction.MEMBER_ADDED, tenantId, TARGET, membership.getId(),
				"user=" + user.getId() + " role=" + request.role()));
		return MembershipResponse.from(membership);
	}

	@Transactional
	public MembershipResponse updateMembership(UUID tenantId, UUID membershipId, UpdateMembershipRequest request) {
		Membership membership = findInTenant(tenantId, membershipId);
		Role actorRole = actorRole(tenantId);
		Role current = membership.getRole();
		Role requested = request.role();

		if (membership.getUser().getId().equals(CurrentUser.id())) {
			throw deny(tenantId, membershipId, "You cannot change your own role");
		}
		if (!actorRole.atLeast(current)) {
			throw deny(tenantId, membershipId, "You cannot change the role of a member ranked above you");
		}
		if (!actorRole.atLeast(requested)) {
			throw deny(tenantId, membershipId, "Cannot grant the " + requested + " role: it is above your own role");
		}
		if (current == requested) {
			return MembershipResponse.from(membership);
		}
		if (current == Role.OWNER) {
			ensureAnotherOwnerRemains(tenantId, "Cannot demote the last owner");
		}

		membership.setRole(requested);
		Membership saved = membershipRepository.save(membership);
		activityService.record(tenantId, null, null, "MEMBER_ROLE_CHANGED",
				"changed " + membership.getUser().getName() + " from " + roleLabel(current) + " to " + roleLabel(requested),
				List.of(Notify.of(membership.getUser().getId(), "MEMBER_ROLE_CHANGED",
						"Your role in " + membership.getTenant().getName() + " is now " + roleLabel(requested), null,
						"/tenants/" + tenantId + "/dashboard",
						"membership:" + membershipId + ":role:" + current + ">" + requested + ":" + java.time.Instant.now().toEpochMilli())));
		auditService.record(Entry.success(AuditAction.MEMBER_ROLE_CHANGED, tenantId, TARGET, membershipId,
				"user=" + membership.getUser().getId() + " from=" + current + " to=" + requested));
		return MembershipResponse.from(saved);
	}

	@Transactional
	public void removeMembership(UUID tenantId, UUID membershipId) {
		Membership membership = findInTenant(tenantId, membershipId);
		Role actorRole = actorRole(tenantId);
		boolean self = membership.getUser().getId().equals(CurrentUser.id());

		if (!self && !actorRole.atLeast(membership.getRole())) {
			throw deny(tenantId, membershipId, "You cannot remove a member ranked above you");
		}
		if (membership.getRole() == Role.OWNER) {
			ensureAnotherOwnerRemains(tenantId, "Cannot remove the last owner");
		}

		UUID userId = membership.getUser().getId();
		// A member who leaves stops owning this workspace's projects; the projects stay,
		// just unassigned (their other data, e.g. created_by history, is untouched).
		projectRepository.clearOwnerInTenant(tenantId, userId);
		membershipRepository.delete(membership);
		// Not workspace-scoped: the removed person can no longer see the workspace, but must
		// still be able to see why it disappeared.
		activityService.record(tenantId, null, null, "MEMBER_REMOVED",
				self ? membership.getUser().getName() + " left the workspace" : "removed " + membership.getUser().getName(),
				self ? List.of() : List.of(new Notify(userId, "MEMBER_REMOVED",
						"You were removed from " + membership.getTenant().getName(), null, null,
						"membership:" + membershipId + ":removed", false)));
		cacheInvalidator.invalidate(tenantId);
		auditService.record(Entry.success(AuditAction.MEMBER_REMOVED, tenantId, TARGET, membershipId,
				"user=" + userId + " role=" + membership.getRole()));
	}

	private static String roleLabel(Role role) {
		return role.name().charAt(0) + role.name().substring(1).toLowerCase();
	}

	private Membership findInTenant(UUID tenantId, UUID membershipId) {
		return membershipRepository.findById(membershipId)
				.filter(m -> m.getTenant().getId().equals(tenantId))
				.orElseThrow(() -> new ResourceNotFoundException("Membership"));
	}

	private Role actorRole(UUID tenantId) {
		return membershipRepository.findByUser_IdAndTenant_Id(CurrentUser.id(), tenantId)
				.map(Membership::getRole)
				.orElseThrow(() -> new ResourceNotFoundException("Membership"));
	}

	/**
	 * Locks the workspace's OWNER memberships for the rest of the transaction, so two owners
	 * demoting or removing each other concurrently cannot both pass the check and leave the
	 * workspace without an owner.
	 */
	private void ensureAnotherOwnerRemains(UUID tenantId, String message) {
		if (membershipRepository.lockByTenantAndRole(tenantId, Role.OWNER).size() <= 1) {
			throw new IllegalArgumentException(message);
		}
	}

	private ForbiddenOperationException deny(UUID tenantId, UUID membershipId, String reason) {
		auditService.recordIndependently(Entry.of(AuditAction.MEMBER_CHANGE_DENIED, Outcome.DENIED, tenantId, TARGET,
				membershipId, reason), CurrentUser.id(), CurrentUser.get().map(u -> u.getEmail()).orElse(null));
		return new ForbiddenOperationException(reason);
	}
}
