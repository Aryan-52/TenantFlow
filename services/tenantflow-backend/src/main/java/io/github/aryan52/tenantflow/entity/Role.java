package io.github.aryan52.tenantflow.entity;

/**
 * Workspace roles, from least to most privileged.
 *
 * <p>Permission rules (enforced by {@code @tenantSecurity} on controllers and by the
 * services for rules that depend on the target resource):
 * <pre>
 * Action                                   MEMBER  ADMIN  OWNER
 * View workspace, projects, tasks, members   yes    yes    yes
 * Create / edit / move tasks, change status  yes    yes    yes
 * Upload attachments, read activity          yes    yes    yes
 * Delete tasks                                no    yes    yes
 * Delete attachments                         own    any    any
 * Create / edit / delete projects             no    yes    yes
 * Edit workspace settings                     no    yes    yes
 * Add / change / remove members               no    yes*   yes*
 * View the workspace audit log                no    yes    yes
 * Delete the workspace                        no     no    yes
 *
 * * Membership changes follow the role hierarchy: nobody can grant a role above their
 *   own, change or remove a member ranked above them, or change their own role; the
 *   last OWNER can never be demoted or removed.
 * </pre>
 */
public enum Role {
	MEMBER,
	ADMIN,
	OWNER;

	/** True when this role is at least as privileged as {@code other}. */
	public boolean atLeast(Role other) {
		return this.ordinal() >= other.ordinal();
	}
}
