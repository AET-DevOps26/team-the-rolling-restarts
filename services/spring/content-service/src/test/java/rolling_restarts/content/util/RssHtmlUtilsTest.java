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
}
