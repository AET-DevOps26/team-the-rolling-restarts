package rolling_restarts.content.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class SourceTest {

	@Test
	void idForRssUrl_isDeterministic() {
		String url = "https://rss.sueddeutsche.de/alles";
		assertEquals(Source.idForRssUrl(url), Source.idForRssUrl(url));
	}

	@Test
	void idForRssUrl_ignoresSurroundingWhitespace() {
		assertEquals(
				Source.idForRssUrl("https://example.com/feed"),
				Source.idForRssUrl("  https://example.com/feed  "));
	}

	@Test
	void idForRssUrl_differsPerUrl() {
		assertNotEquals(
				Source.idForRssUrl("https://example.com/feed"),
				Source.idForRssUrl("https://example.org/feed"));
	}

	@Test
	void idForRssUrl_treatsSchemelessUrlSameAsHttps() {
		assertEquals(
				Source.idForRssUrl("https://example.com/feed"),
				Source.idForRssUrl("example.com/feed"));
	}
}
