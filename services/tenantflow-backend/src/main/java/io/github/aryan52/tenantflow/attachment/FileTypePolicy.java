package io.github.aryan52.tenantflow.attachment;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Server-side file validation. The client's declared content type is ignored: the file's
 * real format is detected from its leading bytes and must be on the allow-list. Formats
 * that browsers can execute (HTML, SVG, JS...) are never accepted.
 *
 * <p>Rules, in order:
 * <ol>
 *   <li>The extension must be allowed.</li>
 *   <li>The content must match the extension. One deliberate exception: an image whose
 *       content is a different allowed image format (a JPEG saved as ".png" is common) is
 *       accepted and stored with the content type of what it really is.</li>
 *   <li>Otherwise the error says what the file actually is, or that it could not be
 *       recognised (damaged or a different format), so the user knows what to do.</li>
 * </ol>
 */
final class FileTypePolicy {

	/** @param label human name used in error messages */
	record FileType(String contentType, String label, byte[][] signatures, boolean text) {
	}

	private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04};
	private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

	static final Map<String, FileType> ALLOWED = Map.ofEntries(
			Map.entry("pdf", new FileType("application/pdf", "PDF document", new byte[][] {{0x25, 0x50, 0x44, 0x46, 0x2D}}, false)),
			Map.entry("png", new FileType("image/png", "PNG image", new byte[][] {{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}}, false)),
			Map.entry("jpg", new FileType("image/jpeg", "JPEG image", new byte[][] {JPEG}, false)),
			Map.entry("jpeg", new FileType("image/jpeg", "JPEG image", new byte[][] {JPEG}, false)),
			Map.entry("gif", new FileType("image/gif", "GIF image", new byte[][] {"GIF87a".getBytes(StandardCharsets.US_ASCII), "GIF89a".getBytes(StandardCharsets.US_ASCII)}, false)),
			Map.entry("webp", new FileType("image/webp", "WebP image", new byte[][] {"RIFF".getBytes(StandardCharsets.US_ASCII)}, false)),
			Map.entry("docx", new FileType("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word document", new byte[][] {ZIP}, false)),
			Map.entry("xlsx", new FileType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel workbook", new byte[][] {ZIP}, false)),
			Map.entry("pptx", new FileType("application/vnd.openxmlformats-officedocument.presentationml.presentation", "PowerPoint presentation", new byte[][] {ZIP}, false)),
			Map.entry("zip", new FileType("application/zip", "ZIP archive", new byte[][] {ZIP}, false)),
			Map.entry("txt", new FileType("text/plain", "text file", null, true)),
			Map.entry("csv", new FileType("text/csv", "CSV file", null, true)),
			Map.entry("md", new FileType("text/markdown", "Markdown file", null, true))
	);

	private static final Set<String> IMAGES = Set.of("png", "jpg", "jpeg", "gif", "webp");
	/** Detection order for binary content (zip-based Office formats are reported as ZIP-based). */
	private static final List<String> DETECTABLE = List.of("pdf", "png", "jpg", "gif", "webp", "zip");

	private FileTypePolicy() {
	}

	/**
	 * Strips any directory components and control/reserved characters, normalises Unicode,
	 * and limits the length. The result is only ever used as display text and in the
	 * Content-Disposition header - never as a storage path.
	 */
	/** Human name of a stored content type, e.g. "PDF document". */
	static String label(String contentType) {
		return ALLOWED.values().stream().filter(t -> t.contentType().equals(contentType)).map(FileType::label)
				.findFirst().orElse(contentType);
	}

	static String sanitizeFilename(String raw) {
		if (raw == null) {
			throw new IllegalArgumentException("The file has no name");
		}
		String name = raw.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1);
		name = Normalizer.normalize(name, Normalizer.Form.NFC);
		name = name.replaceAll("[\\p{Cntrl}\"<>:|?*]", "").trim();
		while (name.startsWith(".")) {
			name = name.substring(1);
		}
		if (name.isBlank()) {
			throw new IllegalArgumentException("The file name is not valid");
		}
		if (name.length() > 200) {
			int dot = name.lastIndexOf('.');
			String ext = dot > 0 ? name.substring(dot) : "";
			name = name.substring(0, 200 - ext.length()) + ext;
		}
		return name;
	}

	/** @return the content type to store and serve the file with; throws IllegalArgumentException when refused. */
	static String validate(String filename, byte[] head) {
		int dot = filename.lastIndexOf('.');
		String ext = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
		FileType type = ALLOWED.get(ext);
		if (type == null) {
			throw new IllegalArgumentException((ext.isEmpty() ? "Files without an extension" : "." + ext + " files")
					+ " can't be uploaded. Allowed types: "
					+ String.join(", ", ALLOWED.keySet().stream().sorted().toList()));
		}
		if (head.length == 0) {
			throw new IllegalArgumentException("The file is empty");
		}

		Optional<String> detected = detect(head);
		if (type.text()) {
			if (detected.isPresent() || !isText(head)) {
				throw new IllegalArgumentException("This is not a plain " + type.label() + ": it contains binary data"
						+ detected.map(d -> " (it looks like a " + ALLOWED.get(d).label() + ")").orElse("") + ".");
			}
			return type.contentType();
		}
		if (matches(type, head)) {
			return type.contentType();
		}
		if (detected.isPresent()) {
			String actual = detected.get();
			FileType actualType = ALLOWED.get(actual);
			if (IMAGES.contains(ext) && IMAGES.contains(actual)) {
				// Mislabelled image: accept it as what it really is.
				return actualType.contentType();
			}
			throw new IllegalArgumentException("This file is a " + actualType.label() + ", but its name ends in ." + ext
					+ ". Rename it to ." + preferredExtension(actual) + " and upload it again.");
		}
		throw new IllegalArgumentException("This doesn't look like a valid " + type.label()
				+ ". The file may be damaged, or it is a different format renamed to ." + ext + ".");
	}

	/** The allowed binary format the content really is, if recognisable. */
	static Optional<String> detect(byte[] head) {
		return DETECTABLE.stream().filter(ext -> matches(ALLOWED.get(ext), head)).findFirst();
	}

	private static boolean matches(FileType type, byte[] head) {
		if (type.text()) {
			return false;
		}
		boolean signature = Arrays.stream(type.signatures()).anyMatch(sig -> startsWith(head, sig));
		// RIFF is shared by WAV/AVI; WebP additionally has "WEBP" at bytes 8-11.
		if (signature && "image/webp".equals(type.contentType())) {
			return head.length >= 12 && new String(head, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
		}
		return signature;
	}

	/** Plain text: no NUL bytes and valid UTF-8 (a character cut off at the end of the sample is fine). */
	private static boolean isText(byte[] head) {
		for (byte b : head) {
			if (b == 0) {
				return false;
			}
		}
		int end = head.length;
		// Drop an incomplete trailing multi-byte sequence caused by sampling.
		for (int i = Math.max(0, end - 3); i < end; i++) {
			int b = head[i] & 0xFF;
			int need = b >= 0xF0 ? 4 : b >= 0xE0 ? 3 : b >= 0xC0 ? 2 : 1;
			if (need > 1 && i + need > end) {
				end = i;
				break;
			}
		}
		try {
			StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(head, 0, end));
			return true;
		} catch (CharacterCodingException e) {
			return false;
		}
	}

	private static String preferredExtension(String detected) {
		return "jpg".equals(detected) ? "jpg" : detected;
	}

	private static boolean startsWith(byte[] data, byte[] prefix) {
		if (data.length < prefix.length) {
			return false;
		}
		for (int i = 0; i < prefix.length; i++) {
			if (data[i] != prefix[i]) {
				return false;
			}
		}
		return true;
	}
}
