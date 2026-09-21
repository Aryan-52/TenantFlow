package io.github.aryan52.tenantflow.membership;

import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.Role;
import io.github.aryan52.tenantflow.entity.Tenant;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.error.DuplicateMembershipException;
import io.github.aryan52.tenantflow.error.ResourceNotFoundException;
import io.github.aryan52.tenantflow.membership.dto.AddMembershipRequest;
import io.github.aryan52.tenantflow.membership.dto.MembershipResponse;
import io.github.aryan52.tenantflow.membership.dto.UpdateMembershipRequest;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.TenantRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MembershipService {

	private final MembershipRepository membershipRepository;
	private final TenantRepository tenantRepository;
	private final UserRepository userRepository;

	@Transactional(readOnly = true)
	public List<MembershipResponse> getMemberships(UUID tenantId) {
		if (!tenantRepository.existsById(tenantId)) {
			throw new ResourceNotFoundException("Tenant");
		}
		return membershipRepository.findByTenant_Id(tenantId).stream()
				.map(MembershipResponse::from)
				.collect(Collectors.toList());
	}

	@Transactional
	public MembershipResponse addMembership(UUID tenantId, AddMembershipRequest request) {
		Tenant tenant = tenantRepository.findById(tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Tenant"));

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
			membership = membershipRepository.save(membership);
			return MembershipResponse.from(membership);
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateMembershipException(user.getEmail());
		}
	}

	@Transactional
	public MembershipResponse updateMembership(UUID tenantId, UUID membershipId, UpdateMembershipRequest request) {
		Membership membership = membershipRepository.findById(membershipId)
				.orElseThrow(() -> new ResourceNotFoundException("Membership"));

		if (!membership.getTenant().getId().equals(tenantId)) {
			throw new ResourceNotFoundException("Membership");
		}

		if (membership.getRole() == Role.OWNER && request.role() != Role.OWNER) {
			if (membershipRepository.countByTenant_IdAndRole(tenantId, Role.OWNER) <= 1) {
				throw new IllegalArgumentException("Cannot demote the last owner");
			}
		}

		membership.setRole(request.role());
		return MembershipResponse.from(membershipRepository.save(membership));
	}

	@Transactional
	public void removeMembership(UUID tenantId, UUID membershipId) {
		Membership membership = membershipRepository.findById(membershipId)
				.orElseThrow(() -> new ResourceNotFoundException("Membership"));

		if (!membership.getTenant().getId().equals(tenantId)) {
			throw new ResourceNotFoundException("Membership");
		}

		if (membership.getRole() == Role.OWNER) {
			if (membershipRepository.countByTenant_IdAndRole(tenantId, Role.OWNER) <= 1) {
				throw new IllegalArgumentException("Cannot remove the last owner");
			}
		}

		membershipRepository.delete(membership);
	}
}
