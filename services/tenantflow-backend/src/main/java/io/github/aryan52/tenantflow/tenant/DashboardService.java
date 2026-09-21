package io.github.aryan52.tenantflow.tenant;

import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import io.github.aryan52.tenantflow.error.ResourceNotFoundException;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import io.github.aryan52.tenantflow.repository.TaskRepository;
import io.github.aryan52.tenantflow.repository.TenantRepository;
import io.github.aryan52.tenantflow.security.AuthenticatedUser;
import io.github.aryan52.tenantflow.tenant.dto.DashboardResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

	private final TenantRepository tenantRepository;
	private final ProjectRepository projectRepository;
	private final TaskRepository taskRepository;
	private final MembershipRepository membershipRepository;

	@Transactional(readOnly = true)
	public DashboardResponse getDashboardData(UUID tenantId) {
		if (!tenantRepository.existsById(tenantId)) {
			throw new ResourceNotFoundException("Tenant");
		}

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();

		Membership membership = membershipRepository.findByUser_IdAndTenant_Id(user.getId(), tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Membership"));

		long totalProjects = projectRepository.countByTenant_Id(tenantId);
		long totalTasks = taskRepository.countByProject_Tenant_Id(tenantId);
		long todoTasks = taskRepository.countByProject_Tenant_IdAndStatus(tenantId, TaskStatus.TODO);
		long inProgressTasks = taskRepository.countByProject_Tenant_IdAndStatus(tenantId, TaskStatus.IN_PROGRESS);
		long doneTasks = taskRepository.countByProject_Tenant_IdAndStatus(tenantId, TaskStatus.DONE);
		long totalMembers = membershipRepository.countByTenant_Id(tenantId);

		return new DashboardResponse(
				totalProjects,
				totalTasks,
				todoTasks,
				inProgressTasks,
				doneTasks,
				totalMembers,
				membership.getRole().name()
		);
	}
}
