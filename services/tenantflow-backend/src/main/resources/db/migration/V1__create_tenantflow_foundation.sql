
CREATE TABLE users (
	id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	email VARCHAR(320) NOT NULL,
	password_hash VARCHAR(255) NOT NULL,
	name VARCHAR(120) NOT NULL,
	created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
	updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE tenants (
	id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	name VARCHAR(160) NOT NULL,
	slug VARCHAR(100) NOT NULL,
	created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
	updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT uk_tenants_slug UNIQUE (slug),
	CONSTRAINT ck_tenants_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE TABLE memberships (
	id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	user_id UUID NOT NULL,
	tenant_id UUID NOT NULL,
	role VARCHAR(32) NOT NULL,
	created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT fk_memberships_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
	CONSTRAINT fk_memberships_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
	CONSTRAINT uk_memberships_user_tenant UNIQUE (user_id, tenant_id),
	CONSTRAINT ck_memberships_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER'))
);

CREATE INDEX idx_memberships_tenant_id ON memberships (tenant_id);
