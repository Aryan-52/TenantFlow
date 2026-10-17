package io.github.aryan52.tenantflow.tenant;

import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.TaskStatus;
import io.github.aryan52.tenantflow.error.ResourceNotFoundException;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import io.github.aryan52.tenantflow.repository.TaskRepository;
import io.github.aryan52.tenantflow.repository.TenantRepository;
import io.github.aryan52.tenantflow.entity.Project;
import io.github.aryan52.tenantflow.security.CurrentUser;
import io.github.aryan52.tenantflow.task.TaskFocus;
import io.github.aryan52.tenantflow.task.TaskService;
import io.github.aryan52.tenantflow.task.dto.TaskResponse;
import io.github.aryan52.tenantflow.tenant.dto.ProjectProgress;
import io.github.aryan52.tenantflow.tenant.dto.DashboardResponse;
import io.github.aryan52.tenantflow.tenant.dto.TenantStats;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

	private final TenantRepository tenantRepository;
	private final ProjectRepository projectRepository;
	private final TaskRepository taskRepository;
	private final MembershipRepository membershipRepository;
	private final TenantStatsService tenantStatsService;

	@Transactional(readOnly = true)
	public DashboardResponse getDashboardData(UUID tenantId) {
		if (!tenantRepository.existsById(tenantId)) {
			throw new ResourceNotFoundException("Tenant");
		}

		UUID userId = CurrentUser.id();
		Membership membership = membershipRepository.findByUser_IdAndTenant_Id(userId, tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Membership"));

		// Tenant-wide counts come from the per-tenant cache. Everything below is either
		// user-specific (role, my work) or a short list, and is never cached.
		TenantStats stats = tenantStatsService.statsFor(tenantId);
		Pageable top = PageRequest.of(0, DashboardResponse.LIST_SIZE, TaskService.BY_DUE_DATE);
		LocalDate dueSoonEnd = LocalDate.now().plusDays(TaskFocus.DUE_SOON_DAYS);

		List<TaskResponse> myTasks = taskRepository
				.findByProject_Tenant_IdAndAssignee_IdAndStatusNot(tenantId, userId, TaskStatus.DONE, top)
				.map(TaskResponse::from).getContent();
		List<TaskResponse> upcoming = taskRepository
				.findByProject_Tenant_IdAndStatusNotAndDueDateLessThanEqual(tenantId, TaskStatus.DONE, dueSoonEnd, top)
				.stream().map(TaskResponse::from).toList();

		return new DashboardResponse(
				stats.totalProjects(),
				stats.totalTasks(),
				stats.todoTasks(),
				stats.inProgressTasks(),
				stats.doneTasks(),
				stats.totalMembers(),
				stats.overdueTasks(),
				stats.dueSoonTasks(),
				membership.getRole().name(),
				taskRepository.countByProject_Tenant_IdAndAssignee_IdAndStatusNot(tenantId, userId, TaskStatus.DONE),
				myTasks,
				upcoming,
				recentProjects(tenantId)
		);
	}

	private List<ProjectProgress> recentProjects(UUID tenantId) {
		List<Project> projects = projectRepository.findByTenant_IdOrderByUpdatedAtDesc(
				tenantId, PageRequest.of(0, DashboardResponse.LIST_SIZE));
		if (projects.isEmpty()) {
			return List.of();
		}
		Map<UUID, TaskRepository.ProjectTaskCounts> counts = taskRepository.countByProjectForTenant(tenantId, TaskStatus.DONE)
				.stream().collect(Collectors.toMap(TaskRepository.ProjectTaskCounts::getProjectId, Function.identity()));
		return projects.stream().map(p -> {
			TaskRepository.ProjectTaskCounts c = counts.get(p.getId());
			return new ProjectProgress(p.getId(), p.getName(), p.getStatus(), p.getOwner() == null ? null : p.getOwner().getName(),
					c == null ? 0 : c.getTotal(), c == null ? 0 : c.getCompleted(), p.getUpdatedAt());
		}).toList();
	}
}
