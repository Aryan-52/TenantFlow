package io.github.aryan52.tenantflow.preferences;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PreferenceService {

	public record Preferences(boolean dueSoonEnabled, int dueSoonDays, boolean overdueEnabled, boolean mentionsEnabled,
			String timeZone, String effectiveTimeZone) {
	}

	public record PreferencesRequest(
			@NotNull Boolean dueSoonEnabled,
			@NotNull @Min(0) @Max(14) Integer dueSoonDays,
			@NotNull Boolean overdueEnabled,
			@NotNull Boolean mentionsEnabled,
			@Size(max = 64) String timeZone) {
	}

	private final NotificationPreferenceRepository repository;

	@Transactional(readOnly = true)
	public Preferences get(UUID userId) {
		return view(load(userId));
	}

	@Transactional
	public Preferences update(UUID userId, PreferencesRequest request) {
		String zone = request.timeZone() == null || request.timeZone().isBlank() ? null : request.timeZone().trim();
		if (zone != null) {
			try {
				ZoneId.of(zone);
			} catch (DateTimeException e) {
				throw new IllegalArgumentException("Unknown time zone: choose one from the list");
			}
		}
		NotificationPreference p = repository.findById(userId).orElseGet(() -> NotificationPreference.defaults(userId));
		p.setDueSoonEnabled(request.dueSoonEnabled());
		p.setDueSoonDays(request.dueSoonDays());
		p.setOverdueEnabled(request.overdueEnabled());
		p.setMentionsEnabled(request.mentionsEnabled());
		p.setTimeZone(zone);
		return view(repository.save(p));
	}

	@Transactional(readOnly = true)
	public NotificationPreference load(UUID userId) {
		return repository.findById(userId).orElseGet(() -> NotificationPreference.defaults(userId));
	}

	/** Preferences of many users in one query; users without a row get the defaults. */
	@Transactional(readOnly = true)
	public Map<UUID, NotificationPreference> loadAll(Collection<UUID> userIds) {
		Map<UUID, NotificationPreference> result = new HashMap<>();
		repository.findByUserIdIn(userIds).forEach(p -> result.put(p.getUserId(), p));
		userIds.forEach(id -> result.putIfAbsent(id, NotificationPreference.defaults(id)));
		return result;
	}

	/** "Today" for this user, in their own time zone. */
	@Transactional(readOnly = true)
	public LocalDate today(UUID userId) {
		return LocalDate.now(zone(load(userId)));
	}

	public static ZoneId zone(NotificationPreference p) {
		try {
			return p.getTimeZone() == null ? ZoneId.systemDefault() : ZoneId.of(p.getTimeZone());
		} catch (DateTimeException e) {
			return ZoneId.systemDefault();
		}
	}

	private static Preferences view(NotificationPreference p) {
		return new Preferences(p.isDueSoonEnabled(), p.getDueSoonDays(), p.isOverdueEnabled(), p.isMentionsEnabled(),
				p.getTimeZone(), zone(p).getId());
	}
}
