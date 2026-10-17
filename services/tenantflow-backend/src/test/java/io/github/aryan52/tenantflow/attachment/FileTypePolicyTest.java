package io.github.aryan52.tenantflow.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FileTypePolicyTest {

	private static final byte[] PDF = "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

	@Test
	void filenamesLoseAnyPathAndUnsafeCharacters() {
		assertThat(FileTypePolicy.sanitizeFilename("../../etc/passwd.txt")).isEqualTo("passwd.txt");
		assertThat(FileTypePolicy.sanitizeFilename("C:\\Users\\me\\report.pdf")).isEqualTo("report.pdf");
		assertThat(FileTypePolicy.sanitizeFilename("in\u0000voice\r\n\".pdf")).isEqualTo("invoice.pdf");
		assertThat(FileTypePolicy.sanitizeFilename("...hidden.txt")).isEqualTo("hidden.txt");
		assertThat(FileTypePolicy.sanitizeFilename("a".repeat(300) + ".pdf")).hasSize(200).endsWith(".pdf");
		assertThatThrownBy(() -> FileTypePolicy.sanitizeFilename("../..")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> FileTypePolicy.sanitizeFilename(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void typeComesFromAnAllowedExtensionAndMustMatchTheContent() {
		assertThat(FileTypePolicy.validate("report.PDF", PDF)).isEqualTo("application/pdf");
		assertThat(FileTypePolicy.validate("shot.png", PNG)).isEqualTo("image/png");
		assertThat(FileTypePolicy.validate("notes.md", "# Notes".getBytes(StandardCharsets.UTF_8))).isEqualTo("text/markdown");

		// Says what the file really is and how to fix it.
		assertThatThrownBy(() -> FileTypePolicy.validate("shot.png", PDF))
				.hasMessage("This file is a PDF document, but its name ends in .png. Rename it to .pdf and upload it again.");
		assertThatThrownBy(() -> FileTypePolicy.validate("notes.txt", new byte[] {'a', 0, 'b'})).hasMessageContaining("binary data");
		assertThatThrownBy(() -> FileTypePolicy.validate("report.pdf", PNG)).hasMessageContaining("is a PNG image");
	}

	@Test
	void aMislabelledImageIsAcceptedAsWhatItReallyIs() {
		byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
		assertThat(FileTypePolicy.validate("screenshot.png", jpeg)).isEqualTo("image/jpeg");
		assertThat(FileTypePolicy.validate("photo.jpg", PNG)).isEqualTo("image/png");
	}

	@Test
	void unrecognisedContentIsReportedAsDamagedOrDifferentFormat() {
		byte[] junk = {1, 2, 3, 4, 5, 6, 7, 8};
		assertThatThrownBy(() -> FileTypePolicy.validate("shot.png", junk))
				.hasMessageContaining("doesn't look like a valid PNG image");
		assertThatThrownBy(() -> FileTypePolicy.validate("deck.pptx", junk))
				.hasMessageContaining("doesn't look like a valid PowerPoint presentation");
		assertThatThrownBy(() -> FileTypePolicy.validate("empty.pdf", new byte[0])).hasMessage("The file is empty");
	}

	@Test
	void textMustBeValidUtf8WithoutBinaryContent() {
		assertThat(FileTypePolicy.validate("data.csv", "név,ár\nkávé,3".getBytes(StandardCharsets.UTF_8))).isEqualTo("text/csv");
		// A multi-byte character cut off by the sampling window is still text.
		byte[] cut = "é".getBytes(StandardCharsets.UTF_8);
		byte[] sample = new byte[] {'a', cut[0]};
		assertThat(FileTypePolicy.validate("notes.txt", sample)).isEqualTo("text/plain");
		assertThatThrownBy(() -> FileTypePolicy.validate("notes.txt", new byte[] {'a', (byte) 0xC3, 'b'})).hasMessageContaining("binary data");
		assertThatThrownBy(() -> FileTypePolicy.validate("notes.md", PDF)).hasMessageContaining("looks like a PDF document");
	}

	@Test
	void executableAndScriptableFormatsAreRefused() {
		for (String name : new String[] {"page.html", "logo.svg", "app.js", "setup.exe", "noextension"}) {
			assertThatThrownBy(() -> FileTypePolicy.validate(name, "x".getBytes(StandardCharsets.US_ASCII)))
					.as(name).hasMessageContaining("can't be uploaded");
		}
	}

	@Test
	void webpMustReallyBeWebpNotAnyRiffFile() {
		byte[] wav = "RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.US_ASCII);
		byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);
		assertThatThrownBy(() -> FileTypePolicy.validate("image.webp", wav)).hasMessageContaining("doesn't look like a valid WebP image");
		assertThat(FileTypePolicy.validate("image.webp", webp)).isEqualTo("image/webp");
	}
}
