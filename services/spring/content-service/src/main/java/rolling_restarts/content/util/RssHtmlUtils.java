package rolling_restarts.content.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;

public final class RssHtmlUtils {

	private static final Pattern IMG_SRC = Pattern.compile(
			"<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

	private RssHtmlUtils() {}

	public static String extractImageUrl(String html) {
		if (html == null || html.isBlank()) {
			return null;
		}
		Matcher matcher = IMG_SRC.matcher(html);
		return matcher.find() ? matcher.group(1) : null;
	}

	/**
	 * Strips HTML markup to plain text using a real HTML5 parser rather than a tag-stripping
	 * regex — a regex pairs any lone {@code <} with the next unrelated {@code >}, corrupting
	 * plain-text descriptions that contain literal comparison operators (e.g. "growth was <5%,
	 * up from >3%").
	 */
	public static String toPlainText(String html) {
		if (html == null || html.isBlank()) {
			return "";
		}
		return Jsoup.parse(html).text();
	}
}
