package io.github.aryan52.tenantflow.search;

import io.github.aryan52.tenantflow.entity.Project;
import io.github.aryan52.tenantflow.entity.Task;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Search queries. Tenant scoping is part of every query (the caller's current memberships),
 * not a filter applied afterwards, so nothing outside the user's workspaces is ever loaded.
 * {@code :pattern} / {@code :prefix} are lower-cased LIKE patterns with wildcards escaped.
 * To-one associations are join-fetched (safe with pagination) so results map without N+1.
 */
public interface SearchRepository extends Repository<Project, UUID> {

	String MEMBER_TENANTS = "(select m.tenant.id from Membership m where m.user.id = :userId)";

	@Query(value = "select p from Project p join fetch p.tenant where p.tenant.id in " + MEMBER_TENANTS
			+ " and (lower(p.name) like :pattern escape '\\' or lower(coalesce(p.description, '')) like :pattern escape '\\')"
			+ " order by case when lower(p.name) like :prefix escape '\\' then 0 else 1 end, p.updatedAt desc",
			countQuery = "select count(p) from Project p where p.tenant.id in " + MEMBER_TENANTS
					+ " and (lower(p.name) like :pattern escape '\\' or lower(coalesce(p.description, '')) like :pattern escape '\\')")
	Page<Project> searchProjects(@Param("userId") UUID userId, @Param("pattern") String pattern,
			@Param("prefix") String prefix, Pageable pageable);

	@Query(value = "select t from Task t join fetch t.project tp join fetch tp.tenant where t.project.tenant.id in " + MEMBER_TENANTS
			+ " and (lower(t.title) like :pattern escape '\\' or lower(coalesce(t.description, '')) like :pattern escape '\\')"
			+ " order by case when lower(t.title) like :prefix escape '\\' then 0 else 1 end, t.updatedAt desc",
			countQuery = "select count(t) from Task t where t.project.tenant.id in " + MEMBER_TENANTS
					+ " and (lower(t.title) like :pattern escape '\\' or lower(coalesce(t.description, '')) like :pattern escape '\\')")
	Page<Task> searchTasks(@Param("userId") UUID userId, @Param("pattern") String pattern,
			@Param("prefix") String prefix, Pageable pageable);

	/** People in the caller's workspaces (any member may already list a workspace's members). */
	@Query(value = "select m from Membership m join fetch m.user join fetch m.tenant where m.tenant.id in " + MEMBER_TENANTS
			+ " and (lower(m.user.name) like :pattern escape '\\' or lower(m.user.email) like :pattern escape '\\')"
			+ " order by case when lower(m.user.name) like :prefix escape '\\' then 0 else 1 end, m.user.name",
			countQuery = "select count(m) from Membership m where m.tenant.id in " + MEMBER_TENANTS
					+ " and (lower(m.user.name) like :pattern escape '\\' or lower(m.user.email) like :pattern escape '\\')")
	Page<io.github.aryan52.tenantflow.entity.Membership> searchMembers(@Param("userId") UUID userId, @Param("pattern") String pattern,
			@Param("prefix") String prefix, Pageable pageable);

	/** Files by name in the caller's workspaces. */
	@Query(value = "select a from Attachment a where a.tenantId in " + MEMBER_TENANTS
			+ " and lower(a.originalFilename) like :pattern escape '\\'"
			+ " order by case when lower(a.originalFilename) like :prefix escape '\\' then 0 else 1 end, a.createdAt desc",
			countQuery = "select count(a) from Attachment a where a.tenantId in " + MEMBER_TENANTS
					+ " and lower(a.originalFilename) like :pattern escape '\\'")
	Page<io.github.aryan52.tenantflow.attachment.Attachment> searchFiles(@Param("userId") UUID userId, @Param("pattern") String pattern,
			@Param("prefix") String prefix, Pageable pageable);
}
