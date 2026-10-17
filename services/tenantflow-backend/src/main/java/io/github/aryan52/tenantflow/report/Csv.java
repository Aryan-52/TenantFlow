package io.github.aryan52.tenantflow.report;

import java.util.List;

/**
 * RFC 4180 CSV with spreadsheet formula-injection protection: a text cell starting with
 * =, +, -, @, tab or carriage return is prefixed with an apostrophe so spreadsheet apps show
 * it as text instead of evaluating it. Numeric columns are written as-is.
 */
public final class Csv {

	private Csv() {
	}

	public static String write(List<Report.Column> columns, List<List<String>> rows) {
		StringBuilder sb = new StringBuilder();
		appendRow(sb, columns.stream().map(Report.Column::label).toList(), null);
		for (List<String> row : rows) {
			appendRow(sb, row, columns);
		}
		return sb.toString();
	}

	private static void appendRow(StringBuilder sb, List<String> cells, List<Report.Column> columns) {
		for (int i = 0; i < cells.size(); i++) {
			if (i > 0) {
				sb.append(',');
			}
			boolean numeric = columns != null && columns.get(i).numeric();
			sb.append(cell(cells.get(i), numeric));
		}
		sb.append("\r\n");
	}

	static String cell(String value, boolean numeric) {
		if (value == null) {
			return "";
		}
		String v = value;
		if (!numeric && !v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
			v = "'" + v;
		}
		if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
			v = "\"" + v.replace("\"", "\"\"") + "\"";
		}
		return v;
	}
}
