package io.github.aryan52.tenantflow.search;

import io.github.aryan52.tenantflow.common.PageResponse;
import io.github.aryan52.tenantflow.common.Pagination;
import io.github.aryan52.tenantflow.entity.Project;
import io.github.aryan52.tenantflow.entity.Task;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SearchService {

	public static final int MIN_QUERY = 2;
	public static final int MAX_QUERY = 100;

	public enum Type { ALL, PROJECT, TASK, MEMBER, FILE }

	public record ProjectHit(UUID id, UUID tenantId, String tenantName, String name, String status, String description) {
	}

	public record TaskHit(UUID id, UUID tenantId, String tenantName, UUID projectId, String projectName, String title,
			String status, String priority, LocalDate dueDate) {
	}

	public record MemberHit(UUID userId, UUID tenantId, String tenantName, String name, String email, String role) {
	}

	public record FileHit(UUID id, UUID tenantId, String tenantName, UUID projectId, String projectName, UUID taskId,
			String filename, String contentType, long sizeBytes) {
	}

	/** Results grouped by type; a group is null when filtered out by {@code type}. */
	public record SearchResponse(String query, PageResponse<ProjectHit> projects, PageResponse<TaskHit> tasks,
			PageResponse<MemberHit> members, PageResponse<FileHit> files) {
	}

	private final SearchRepository repository;
	private final io.github.aryan52.tenantflow.repository.ProjectRepository projectRepository;

	@Transactional(readOnly = true)
	public SearchResponse search(UUID userId, String rawQuery, Type type, Integer page, Integer size) {
		String query = rawQuery == null ? "" : rawQuery.strip();
		if (query.length() < MIN_QUERY || query.length() > MAX_QUERY) {
			throw new IllegalArgumentException("Search text must be between " + MIN_QUERY + " and " + MAX_QUERY + " characters");
		}
		String escaped = escapeLike(query.toLowerCase(Locale.ROOT));
		String pattern = "%" + escaped + "%";
		String prefix = escaped + "%";
		var pageable = Pagination.of(page, size == null ? 5 : size, Sort.unsorted());

		PageResponse<ProjectHit> projects = wants(type, Type.PROJECT)
				? PageResponse.of(repository.searchProjects(userId, pattern, prefix, pageable), SearchService::toHit) : null;
		PageResponse<TaskHit> tasks = wants(type, Type.TASK)
				? PageResponse.of(repository.searchTasks(userId, pattern, prefix, pageable), SearchService::toHit) : null;
		PageResponse<MemberHit> members = wants(type, Type.MEMBER)
				? PageResponse.of(repository.searchMembers(userId, pattern, prefix, pageable), m -> new MemberHit(m.getUser().getId(),
						m.getTenant().getId(), m.getTenant().getName(), m.getUser().getName(), m.getUser().getEmail(), m.getRole().name()))
				: null;
		PageResponse<FileHit> files = null;
		if (wants(type, Type.FILE)) {
			var found = repository.searchFiles(userId, pattern, prefix, pageable);
			// One query for the projects (and their workspaces) of this page of files.
			java.util.Map<UUID, Project> byId = new java.util.HashMap<>();
			projectRepository.findAllById(found.getContent().stream().map(io.github.aryan52.tenantflow.attachment.Attachment::getProjectId)
					.collect(java.util.stream.Collectors.toSet())).forEach(p -> byId.put(p.getId(), p));
			files = PageResponse.of(found, a -> {
				Project p = byId.get(a.getProjectId());
				return new FileHit(a.getId(), a.getTenantId(), p == null ? null : p.getTenant().getName(), a.getProjectId(),
						p == null ? null : p.getName(), a.getTaskId(), a.getOriginalFilename(), a.getContentType(), a.getSizeBytes());
			});
		}
		return new SearchResponse(query, projects, tasks, members, files);
	}

	private static boolean wants(Type requested, Type group) {
		return requested == Type.ALL || requested == group;
	}

	/** User text is matched literally: LIKE wildcards and the escape character are escaped. */
	static String escapeLike(String s) {
		return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private static ProjectHit toHit(Project p) {
		String d = p.getDescription();
		return new ProjectHit(p.getId(), p.getTenant().getId(), p.getTenant().getName(), p.getName(), p.getStatus().name(),
				d == null ? null : d.length() > 160 ? d.substring(0, 159) + "…" : d);
	}

	private static TaskHit toHit(Task t) {
		Project p = t.getProject();
		return new TaskHit(t.getId(), p.getTenant().getId(), p.getTenant().getName(), p.getId(), p.getName(), t.getTitle(),
				t.getStatus().name(), t.getPriority().name(), t.getDueDate());
	}
}
