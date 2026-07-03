package rolling_restarts.content.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Normalizes user-supplied RSS feed URLs before validation and persistence. */
public final class RssUrlNormalizer {

	private RssUrlNormalizer() {}

	/**
	 * Trims whitespace and prepends {@code https://} when no scheme is present
	 * (e.g. {@code rss.example.com/feed}).
	 */
	public static String normalize(String url) {
		if (url == null) {
			return "";
		}
		String trimmed = url.trim();
		if (trimmed.isEmpty()) {
			return trimmed;
		}
		int schemeEnd = trimmed.indexOf("://");
		if (schemeEnd > 0 && trimmed.substring(0, schemeEnd).chars().allMatch(ch ->
				Character.isLetterOrDigit(ch) || ch == '+' || ch == '-' || ch == '.')) {
			return trimmed;
		}
		return "https://" + trimmed;
	}

	/**
	 * Returns URLs to try when fetching a feed: {@code https} first, then {@code http} as a
	 * fallback. Both variants share the same host, port, path, and query.
	 */
	public static List<String> fetchCandidates(String url) {
		String normalized = normalize(url);
		if (normalized.isEmpty()) {
			return List.of();
		}

		URI uri = URI.create(normalized);
		String host = uri.getHost();
		if (host == null) {
			return List.of(normalized);
		}

		String authority = uri.getPort() == -1 ? host : host + ":" + uri.getPort();
		String path = uri.getRawPath() != null ? uri.getRawPath() : "";
		StringBuilder suffix = new StringBuilder(path);
		if (uri.getRawQuery() != null) {
			suffix.append('?').append(uri.getRawQuery());
		}
		if (uri.getRawFragment() != null) {
			suffix.append('#').append(uri.getRawFragment());
		}

		Set<String> ordered = new LinkedHashSet<>();
		ordered.add("https://" + authority + suffix);
		ordered.add("http://" + authority + suffix);
		return new ArrayList<>(ordered);
	}
}
