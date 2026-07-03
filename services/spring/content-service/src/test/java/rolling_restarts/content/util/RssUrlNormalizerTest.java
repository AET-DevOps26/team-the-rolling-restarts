package rolling_restarts.content.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class RssUrlNormalizerTest {

	@Test
	void normalize_prependsHttpsWhenSchemeMissing() {
		assertEquals("https://rss.example.com/feed", RssUrlNormalizer.normalize("rss.example.com/feed"));
	}

	@Test
	void normalize_preservesExplicitScheme() {
		assertEquals("http://example.com/feed", RssUrlNormalizer.normalize("http://example.com/feed"));
	}

	@Test
	void fetchCandidates_triesHttpsBeforeHttp() {
		List<String> candidates = RssUrlNormalizer.fetchCandidates("http://example.com/feed");
		assertEquals(List.of("https://example.com/feed", "http://example.com/feed"), candidates);
	}

	@Test
	void fetchCandidates_includesPort() {
		List<String> candidates = RssUrlNormalizer.fetchCandidates("https://example.com:8080/feed");
		assertTrue(candidates.contains("https://example.com:8080/feed"));
		assertTrue(candidates.contains("http://example.com:8080/feed"));
	}

	@Test
	void fetchCandidates_deduplicatesWhenAlreadyHttps() {
		List<String> candidates = RssUrlNormalizer.fetchCandidates("https://example.com/feed");
		assertEquals(2, candidates.size());
		assertEquals("https://example.com/feed", candidates.get(0));
		assertEquals("http://example.com/feed", candidates.get(1));
	}
}
