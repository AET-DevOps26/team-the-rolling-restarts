package rolling_restarts.content.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class RssHtmlUtilsTest {

	@Test
	void extractImageUrl_readsFirstImgSrc() {
		String html = "<img src=\"https://example.com/a.jpg\" /><p>Text</p>";
		assertEquals("https://example.com/a.jpg", RssHtmlUtils.extractImageUrl(html));
	}

	@Test
	void extractImageUrl_returnsNullForPlainText() {
		assertNull(RssHtmlUtils.extractImageUrl("No image here"));
	}

	@Test
	void toPlainText_stripsTags() {
		assertEquals("Hello world", RssHtmlUtils.toPlainText("<p>Hello <strong>world</strong></p>"));
	}

	@Test
	void toPlainText_preservesLiteralAngleBrackets() {
		// A naive tag-stripping regex pairs the first "<" with the next unrelated ">" and deletes
		// everything between them; a real HTML5 parser knows "<5" isn't a tag-open.
		assertEquals(
				"Growth was <5% this quarter, up from >3% last year",
				RssHtmlUtils.toPlainText("Growth was <5% this quarter, up from >3% last year"));
	}

	@Test
	void toPlainText_decodesHtmlEntities() {
		assertEquals("Q&A <3", RssHtmlUtils.toPlainText("<p>Q&amp;A &lt;3</p>"));
	}

	@Test
	void toPlainText_handlesNbsp() {
		// Jsoup normalizes &nbsp; to a plain ASCII space in .text() (verified: 0x20, not
		// U+00A0) -- pinned down empirically rather than assumed.
		assertEquals("Hello\u0020world", RssHtmlUtils.toPlainText("<p>Hello&nbsp;world</p>"));
	}
}
