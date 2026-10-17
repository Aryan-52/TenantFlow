package io.github.aryan52.tenantflow.project.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.aryan52.tenantflow.entity.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code status} is optional and defaults to ACTIVE.
 * {@code ownerId}: omitted = the creator owns the project, {@code null} = unassigned,
 * a user id = that workspace member. A class rather than a record so that "omitted" can be
 * told apart from an explicit {@code null} (see {@link #isOwnerIdPresent()}).
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateProjectRequest {

	@NotBlank
	@Size(max = 160)
	private String name;

	@Size(max = 2000)
	private String description;

	private ProjectStatus status;

	/** Planning dates (optional). On update, an omitted date keeps the current value; null clears it. */
	@Setter(lombok.AccessLevel.NONE)
	private java.time.LocalDate startDate;

	@Setter(lombok.AccessLevel.NONE)
	// Set only by the setter above when JSON contains the field. READ_ONLY: never accepted from
	// JSON (a client could otherwise "clear" a field without sending it), but still part of the
	// serialised request that IdempotencyService fingerprints, where omitted vs null matters.
	@JsonProperty(access = JsonProperty.Access.READ_ONLY)
	private boolean startDatePresent;

	@Setter(lombok.AccessLevel.NONE)
	private java.time.LocalDate targetDate;

	@Setter(lombok.AccessLevel.NONE)
	// Set only by the setter above when JSON contains the field. READ_ONLY: never accepted from
	// JSON (a client could otherwise "clear" a field without sending it), but still part of the
	// serialised request that IdempotencyService fingerprints, where omitted vs null matters.
	@JsonProperty(access = JsonProperty.Access.READ_ONLY)
	private boolean targetDatePresent;

	public void setStartDate(java.time.LocalDate startDate) {
		this.startDate = startDate;
		this.startDatePresent = true;
	}

	public void setTargetDate(java.time.LocalDate targetDate) {
		this.targetDate = targetDate;
		this.targetDatePresent = true;
	}

	@Setter(lombok.AccessLevel.NONE)
	private UUID ownerId;

	@Setter(lombok.AccessLevel.NONE)
	// Set only by the setter above when JSON contains the field. READ_ONLY: never accepted from
	// JSON (a client could otherwise "clear" a field without sending it), but still part of the
	// serialised request that IdempotencyService fingerprints, where omitted vs null matters.
	@JsonProperty(access = JsonProperty.Access.READ_ONLY)
	private boolean ownerIdPresent;

	/** Called by Jackson only when the JSON contains "ownerId" (including an explicit null). */
	public void setOwnerId(UUID ownerId) {
		this.ownerId = ownerId;
		this.ownerIdPresent = true;
	}
}
