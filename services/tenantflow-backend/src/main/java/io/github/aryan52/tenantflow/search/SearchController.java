package io.github.aryan52.tenantflow.search;

import io.github.aryan52.tenantflow.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Global search across every workspace the caller currently belongs to (rate limited per user). */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

	private final SearchService searchService;

	@GetMapping
	public SearchService.SearchResponse search(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam("q") String query,
			@RequestParam(defaultValue = "ALL") SearchService.Type type,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size
	) {
		return searchService.search(user.getId(), query, type, page, size);
	}
}
