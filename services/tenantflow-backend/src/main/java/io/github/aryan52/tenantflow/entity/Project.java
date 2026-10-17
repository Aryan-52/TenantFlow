package io.github.aryan52.tenantflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "projects")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Project {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
			name = "tenant_id",
			nullable = false,
			foreignKey = @ForeignKey(name = "fk_projects_tenant")
	)
	private Tenant tenant;

	@NotBlank
	@Size(max = 160)
	@Column(nullable = false, length = 160)
	private String name;

	@Size(max = 2000)
	@Column(length = 2000)
	private String description;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	@Builder.Default
	private ProjectStatus status = ProjectStatus.ACTIVE;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(
			name = "created_by",
			foreignKey = @ForeignKey(name = "fk_projects_created_by")
	)
	private User createdBy;

	/** Project owner / lead: an assignable workspace member; null = unassigned. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(
			name = "owner_id",
			foreignKey = @ForeignKey(name = "fk_projects_owner")
	)
	private User owner;

	/** Planning dates (V16); optional, target on or after start. */
	@Column(name = "start_date")
	private java.time.LocalDate startDate;

	@Column(name = "target_date")
	private java.time.LocalDate targetDate;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
	private Instant updatedAt;

	/** Optimistic-locking version (V9). Incremented by Hibernate on every update. */
	@Version
	@Column(nullable = false)
	private long version;

	@PrePersist
	void onCreate() {
		Instant now = io.github.aryan52.tenantflow.common.Timestamps.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = io.github.aryan52.tenantflow.common.Timestamps.now();
	}
}
