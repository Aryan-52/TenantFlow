package io.github.aryan52.tenantflow.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Validates client-supplied paging parameters and caps the page size. */
public final class Pagination {

	public static final int DEFAULT_SIZE = 20;
	public static final int MAX_SIZE = 100;

	private Pagination() {
	}

	public static Pageable of(Integer page, Integer size, Sort sort) {
		int p = page == null ? 0 : page;
		int s = size == null ? DEFAULT_SIZE : size;
		if (p < 0) {
			throw new IllegalArgumentException("page must be 0 or greater");
		}
		if (s < 1 || s > MAX_SIZE) {
			throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
		}
		return PageRequest.of(p, s, sort);
	}
}
