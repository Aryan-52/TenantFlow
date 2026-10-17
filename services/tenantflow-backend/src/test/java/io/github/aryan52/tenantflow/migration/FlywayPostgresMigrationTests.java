package io.github.aryan52.tenantflow.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs the real Flyway migrations against a disposable PostgreSQL 17 (never the developer's
 * database): from an empty schema, and as an upgrade of a populated V7 database, which is
 * what existing installations will experience.
 */
@Testcontainers(disabledWithoutDocker = true)
class FlywayPostgresMigrationTests {

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

	private String createDatabase() throws SQLException {
		String name = "db_" + UUID.randomUUID().toString().replace("-", "");
		try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
			 Statement s = c.createStatement()) {
			s.execute("CREATE DATABASE " + name);
		}
		return POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
	}

	private Flyway flyway(String url, String target) {
		var config = Flyway.configure()
				.dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
				.locations("classpath:db/migration");
		if (target != null) {
			config.target(target);
		}
		return config.load();
	}

	private long count(Connection c, String sql) throws SQLException {
		try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
			rs.next();
			return rs.getLong(1);
		}
	}

	@Test
	void allMigrationsApplyToAnEmptyDatabase() throws Exception {
		String url = createDatabase();
		var result = flyway(url, null).migrate();

		assertThat(result.success).isTrue();
		var applied = flyway(url, null).info().applied();
		assertThat(applied).allSatisfy(m -> assertThat(m.getState().isApplied()).isTrue());
		try (Connection c = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword())) {
			assertThat(count(c, "select count(*) from information_schema.tables where table_name in ('audit_events', 'activity_events', 'notifications', 'idempotency_keys', 'attachments', 'task_comments')")).isEqualTo(6);
			assertThat(count(c, "select count(*) from information_schema.columns where table_name in ('projects','tasks') and column_name = 'version'")).isEqualTo(2);
			// V16: planning, collaboration and automation tables; new columns are all nullable.
			assertThat(count(c, "select count(*) from information_schema.tables where table_name in ('project_milestones', 'task_dependencies', 'task_focus', 'notification_preferences', 'automation_rules', 'automation_runs')")).isEqualTo(6);
			assertThat(count(c, "select count(*) from information_schema.columns where ((table_name = 'tasks' and column_name in ('start_date','milestone_id','recurrence_frequency','series_id')) or (table_name = 'projects' and column_name in ('start_date','target_date')) or (table_name = 'memberships' and column_name = 'weekly_capacity')) and is_nullable = 'YES'")).isEqualTo(7);
		}
	}

	/**
	 * V7 backfills project owners from created_by. The creator becomes the owner only while
	 * still a member of that project's workspace; everything else stays unassigned, and no
	 * project or task is changed or removed.
	 */
	@Test
	void upgradingAPopulatedV6DatabaseBackfillsOwnersOnlyFromCurrentMembers() throws Exception {
		String url = createDatabase();
		flyway(url, "6").migrate();

		UUID member = UUID.randomUUID();
		UUID formerMember = UUID.randomUUID();
		UUID tenant = UUID.randomUUID();
		UUID otherTenant = UUID.randomUUID();
		UUID byMember = UUID.randomUUID();
		UUID byFormerMember = UUID.randomUUID();
		UUID noCreator = UUID.randomUUID();
		UUID memberElsewhereOnly = UUID.randomUUID();
		try (Connection c = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
			 Statement s = c.createStatement()) {
			s.execute("insert into users (id, email, name, password_hash) values ('" + member + "', 'm@example.com', 'M', 'hash-m'), ('"
					+ formerMember + "', 'f@example.com', 'F', 'hash-f')");
			s.execute("insert into tenants (id, name, slug) values ('" + tenant + "', 'T', 't'), ('" + otherTenant + "', 'O', 'o')");
			// formerMember belongs only to the other workspace now.
			s.execute("insert into memberships (user_id, tenant_id, role) values ('" + member + "', '" + tenant + "', 'OWNER'), ('"
					+ formerMember + "', '" + otherTenant + "', 'MEMBER')");
			s.execute("insert into projects (id, tenant_id, name, status, created_by) values"
					+ " ('" + byMember + "', '" + tenant + "', 'By member', 'ACTIVE', '" + member + "'),"
					+ " ('" + byFormerMember + "', '" + tenant + "', 'By former member', 'ON_HOLD', '" + formerMember + "'),"
					+ " ('" + noCreator + "', '" + tenant + "', 'Legacy', 'ACTIVE', null),"
					+ " ('" + memberElsewhereOnly + "', '" + otherTenant + "', 'Elsewhere', 'ACTIVE', '" + member + "')");
			s.execute("insert into tasks (project_id, title, status, priority, assignee_id, created_by, due_date) values ('" + byFormerMember
					+ "', 'Keep me', 'TODO', 'HIGH', '" + formerMember + "', '" + formerMember + "', date '2026-11-30')");
		}

		var result = flyway(url, null).migrate();
		assertThat(result.success).isTrue();

		try (Connection c = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword())) {
			assertThat(count(c, "select count(*) from projects where id = '" + byMember + "' and owner_id = '" + member + "'")).isEqualTo(1);
			// Creator no longer in this workspace, no creator, or creator only a member elsewhere: unassigned.
			assertThat(count(c, "select count(*) from projects where id in ('" + byFormerMember + "', '" + noCreator + "', '"
					+ memberElsewhereOnly + "') and owner_id is null")).isEqualTo(3);
			// Nothing else changed: creators, names, statuses, the task and the accounts are intact.
			assertThat(count(c, "select count(*) from projects")).isEqualTo(4);
			assertThat(count(c, "select count(*) from projects where id = '" + byFormerMember + "' and created_by = '" + formerMember
					+ "' and name = 'By former member' and status = 'ON_HOLD'")).isEqualTo(1);
			assertThat(count(c, "select count(*) from tasks where title = 'Keep me' and assignee_id = '" + formerMember
					+ "' and created_by = '" + formerMember + "' and due_date = date '2026-11-30'")).isEqualTo(1);
			assertThat(count(c, "select count(*) from users where password_hash in ('hash-m', 'hash-f')")).isEqualTo(2);
			assertThat(count(c, "select count(*) from memberships")).isEqualTo(2);
		}
	}

	@Test
	void upgradingAPopulatedV7DatabasePreservesAllData() throws Exception {
		String url = createDatabase();
		flyway(url, "7").migrate();

		UUID user = UUID.randomUUID();
		UUID tenant = UUID.randomUUID();
		UUID project = UUID.randomUUID();
		try (Connection c = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
			 Statement s = c.createStatement()) {
			s.execute("insert into users (id, email, name, password_hash) values ('" + user + "', 'a@example.com', 'A', 'hash')");
			s.execute("insert into tenants (id, name, slug) values ('" + tenant + "', 'T', 't')");
			s.execute("insert into memberships (user_id, tenant_id, role) values ('" + user + "', '" + tenant + "', 'OWNER')");
			s.execute("insert into projects (id, tenant_id, name, status, created_by, owner_id) values ('" + project + "', '" + tenant
					+ "', 'P', 'ACTIVE', '" + user + "', '" + user + "')");
			s.execute("insert into tasks (project_id, title, status, priority, assignee_id, due_date) values ('" + project
					+ "', 'Task', 'IN_PROGRESS', 'URGENT', '" + user + "', date '2026-12-31')");
		}

		var result = flyway(url, null).migrate();
		assertThat(result.success).isTrue();
		assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(2);

		try (Connection c = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword())) {
			assertThat(count(c, "select count(*) from users")).isEqualTo(1);
			assertThat(count(c, "select count(*) from memberships")).isEqualTo(1);
			assertThat(count(c, "select count(*) from projects where owner_id = '" + user + "' and version = 0")).isEqualTo(1);
			assertThat(count(c, "select count(*) from tasks where priority = 'URGENT' and due_date = date '2026-12-31' and version = 0")).isEqualTo(1);
			assertThat(count(c, "select count(*) from audit_events")).isZero();
			// V16 adds no values to existing rows: planning fields stay empty until someone sets them.
			assertThat(count(c, "select count(*) from tasks where start_date is null and milestone_id is null and series_id is null and recurrence_frequency is null")).isEqualTo(1);
			assertThat(count(c, "select count(*) from projects where start_date is null and target_date is null")).isEqualTo(1);
		}
	}
}
