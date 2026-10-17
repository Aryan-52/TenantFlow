package io.github.aryan52.tenantflow.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvTest {

	@Test
	void escapesQuotesSeparatorsAndNewlines() {
		assertThat(Csv.cell("plain", false)).isEqualTo("plain");
		assertThat(Csv.cell("a,b", false)).isEqualTo("\"a,b\"");
		assertThat(Csv.cell("say \"hi\"", false)).isEqualTo("\"say \"\"hi\"\"\"");
		assertThat(Csv.cell("two\nlines", false)).isEqualTo("\"two\nlines\"");
		assertThat(Csv.cell(null, false)).isEmpty();
	}

	@Test
	void neutralisesFormulasInTextButNotInNumbers() {
		for (String risky : List.of("=1+1", "+1", "-1", "@SUM(A1)", "\tx", "\rx")) {
			assertThat(Csv.cell(risky, false)).as(risky).contains("'" + risky.charAt(0));
		}
		assertThat(Csv.cell("-3", true)).isEqualTo("-3");
	}

	@Test
	void writesHeaderAndRowsWithCrlf() {
		String csv = Csv.write(List.of(Report.Column.text("a", "Name"), Report.Column.number("b", "Count")),
				List.of(List.of("x", "2")));
		assertThat(csv).isEqualTo("Name,Count\r\nx,2\r\n");
	}
}
