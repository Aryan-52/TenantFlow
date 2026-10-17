package io.github.aryan52.tenantflow.attachment;

import io.github.aryan52.tenantflow.common.PageResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The workspace's document center: every file of every project and task in one list, with
 * search, filters and sorting. Read-only; download and delete use the project attachment
 * endpoints, which apply their own checks. Every query is restricted to one workspace.
 * The query is built from fixed fragments (never from user text); search text is a bound,
 * escaped LIKE parameter.
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

	public record DocumentResponse(UUID id, UUID projectId, String projectName, UUID taskId, String taskTitle,
			String filename, String contentType, long sizeBytes, UUID uploadedById, String uploadedByName,
			Instant createdAt) {
	}

	/** File type groups, matched on the stored (sniffed) content type. */
	static final Map<String, String> TYPE_FILTERS = Map.of(
			"image", "a.contentType like 'image/%'",
			"pdf", "a.contentType = 'application/pdf'",
			"text", "(a.contentType like 'text/%' or a.contentType = 'application/json')",
			"office", "(a.contentType like 'application/vnd.%' or a.contentType = 'application/msword')",
			"archive", "(a.contentType like '%zip%')");

	static final Map<String, String> SORTS = Map.of(
			"newest", "a.createdAt desc, a.id",
			"oldest", "a.createdAt asc, a.id",
			"largest", "a.sizeBytes desc, a.id",
			"smallest", "a.sizeBytes asc, a.id",
			"name", "lower(a.originalFilename) asc, a.id");

	private final EntityManager em;

	@Transactional(readOnly = true)
	public PageResponse<DocumentResponse> list(UUID tenantId, String q, UUID projectId, String type, UUID uploadedBy,
			String sort, int page, int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw new IllegalArgumentException("page must be >= 0 and size between 1 and 100");
		}
		String order = SORTS.get(sort.toLowerCase(Locale.ROOT));
		if (order == null) {
			throw new IllegalArgumentException("sort must be one of: " + String.join(", ", SORTS.keySet()));
		}
		String typeClause = null;
		if (type != null && !type.isBlank()) {
			typeClause = TYPE_FILTERS.get(type.toLowerCase(Locale.ROOT));
			if (typeClause == null) {
				throw new IllegalArgumentException("type must be one of: " + String.join(", ", TYPE_FILTERS.keySet()));
			}
		}
		String term = q == null ? "" : q.trim();
		if (term.length() > 100) {
			throw new IllegalArgumentException("Search text is too long");
		}

		StringBuilder where = new StringBuilder(" where a.tenantId = :tenantId");
		if (projectId != null) {
			where.append(" and a.projectId = :projectId");
		}
		if (uploadedBy != null) {
			where.append(" and a.uploadedBy = :uploadedBy");
		}
		if (!term.isEmpty()) {
			where.append(" and lower(a.originalFilename) like :q escape '\\'");
		}
		if (typeClause != null) {
			where.append(" and ").append(typeClause);
		}

		TypedQuery<Attachment> query = bind(em.createQuery("select a from Attachment a" + where + " order by " + order,
				Attachment.class), tenantId, projectId, uploadedBy, term);
		TypedQuery<Long> count = bind(em.createQuery("select count(a) from Attachment a" + where, Long.class),
				tenantId, projectId, uploadedBy, term);
		List<Attachment> items = query.setFirstResult(page * size).setMaxResults(size).getResultList();
		long total = count.getSingleResult();

		Map<UUID, String> projects = names(items.stream().map(Attachment::getProjectId).collect(Collectors.toSet()),
				"select p.id, p.name from Project p where p.tenant.id = :tenantId and p.id in :ids", tenantId);
		Map<UUID, String> tasks = names(items.stream().map(Attachment::getTaskId).filter(Objects::nonNull).collect(Collectors.toSet()),
				"select t.id, t.title from Task t where t.project.tenant.id = :tenantId and t.id in :ids", tenantId);
		Map<UUID, String> users = new HashMap<>();
		var userIds = items.stream().map(Attachment::getUploadedBy).filter(Objects::nonNull).collect(Collectors.toSet());
		if (!userIds.isEmpty()) {
			em.createQuery("select u.id, u.name from User u where u.id in :ids", Object[].class)
					.setParameter("ids", userIds).getResultList()
					.forEach(r -> users.put((UUID) r[0], (String) r[1]));
		}
		List<DocumentResponse> content = items.stream().map(a -> new DocumentResponse(a.getId(), a.getProjectId(),
				projects.get(a.getProjectId()), a.getTaskId(), a.getTaskId() == null ? null : tasks.get(a.getTaskId()),
				a.getOriginalFilename(), a.getContentType(), a.getSizeBytes(), a.getUploadedBy(),
				users.get(a.getUploadedBy()), a.getCreatedAt())).toList();
		int totalPages = (int) Math.ceil(total / (double) size);
		return new PageResponse<>(content, page, size, total, totalPages, page + 1 < totalPages);
	}

	private static <T> TypedQuery<T> bind(TypedQuery<T> q, UUID tenantId, UUID projectId, UUID uploadedBy, String term) {
		q.setParameter("tenantId", tenantId);
		if (projectId != null) {
			q.setParameter("projectId", projectId);
		}
		if (uploadedBy != null) {
			q.setParameter("uploadedBy", uploadedBy);
		}
		if (!term.isEmpty()) {
			String escaped = term.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
			q.setParameter("q", "%" + escaped + "%");
		}
		return q;
	}

	private Map<UUID, String> names(java.util.Set<UUID> ids, String jpql, UUID tenantId) {
		Map<UUID, String> result = new HashMap<>();
		if (!ids.isEmpty()) {
			em.createQuery(jpql, Object[].class).setParameter("tenantId", tenantId).setParameter("ids", ids)
					.getResultList().forEach(r -> result.put((UUID) r[0], (String) r[1]));
		}
		return result;
	}
}
