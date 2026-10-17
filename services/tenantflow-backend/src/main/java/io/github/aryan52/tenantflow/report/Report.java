package io.github.aryan52.tenantflow.report;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** A tabular report: stable column keys (also the CSV header labels' order) and string cells. */
public record Report(String type, String title, String description, List<Column> columns, List<List<String>> rows,
		Map<String, String> filters, Instant generatedAt) {

	public record Column(String key, String label, boolean numeric) {
		static Column text(String key, String label) {
			return new Column(key, label, false);
		}

		static Column number(String key, String label) {
			return new Column(key, label, true);
		}
	}
}
