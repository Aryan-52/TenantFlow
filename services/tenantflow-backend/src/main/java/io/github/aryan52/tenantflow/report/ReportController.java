package io.github.aryan52.tenantflow.report;

import io.github.aryan52.tenantflow.report.ReportService.Filters;
import io.github.aryan52.tenantflow.report.ReportService.Type;
import io.github.aryan52.tenantflow.security.AuthenticatedUser;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/{tenantId}/reports")
@RequiredArgsConstructor
public class ReportController {

	private final ReportService reportService;

	@GetMapping("/{type}")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public Report report(@PathVariable UUID tenantId, @PathVariable String type, @AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) UUID projectId, @RequestParam(required = false) UUID assigneeId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return reportService.build(tenantId, user.getId(), Type.fromSlug(type), new Filters(projectId, assigneeId, from, to));
	}

	/** The same report as CSV (UTF-8 with BOM so spreadsheet apps detect the encoding). */
	@GetMapping("/{type}/csv")
	@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")
	public ResponseEntity<byte[]> csv(@PathVariable UUID tenantId, @PathVariable String type, @AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) UUID projectId, @RequestParam(required = false) UUID assigneeId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		Report r = reportService.build(tenantId, user.getId(), Type.fromSlug(type), new Filters(projectId, assigneeId, from, to));
		byte[] body = ("﻿" + Csv.write(r.columns(), r.rows())).getBytes(StandardCharsets.UTF_8);
		String filename = "tenantflow-" + r.type() + "-" + LocalDate.now() + ".csv";
		return ResponseEntity.ok()
				.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
				.header("X-Content-Type-Options", "nosniff")
				.body(body);
	}
}
