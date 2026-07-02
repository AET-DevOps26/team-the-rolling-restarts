package rolling_restarts.content.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RssHtmlUtils {

	private static final Pattern IMG_SRC = Pattern.compile(
			"<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
	private static final Pattern HTML_TAGS = Pattern.compile("<[^>]+>");

	private RssHtmlUtils() {}

	public static String extractImageUrl(String html) {
		if (html == null || html.isBlank()) {
			return null;
		}
		Matcher matcher = IMG_SRC.matcher(html);
		return matcher.find() ? matcher.group(1) : null;
	}

	public static String toPlainText(String html) {
		if (html == null || html.isBlank()) {
			return "";
		}
		return HTML_TAGS.matcher(html).replaceAll(" ").replaceAll("\\s+", " ").trim();
	}
}
