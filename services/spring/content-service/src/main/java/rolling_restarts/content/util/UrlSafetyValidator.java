package rolling_restarts.content.util;

import java.net.InetAddress;
import java.net.URI;

/**
 * Guards outbound RSS fetches against SSRF. A user controls the {@code rssUrl} of a
 * {@link rolling_restarts.content.model.Source}, so every URL is validated both when the source is
 * created and again immediately before each fetch (DNS can re-resolve to an internal address
 * between those two points). Only public http/https hosts are allowed.
 */
public final class UrlSafetyValidator {

	private UrlSafetyValidator() {}

	/**
	 * Validates that {@code url} is an http/https URL whose host resolves only to public,
	 * routable addresses. Throws {@link IllegalArgumentException} otherwise.
	 */
	public static void validatePublicUrl(String url) {
		final URI uri;
		try {
			uri = URI.create(url);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid RSS URL");
		}

		String scheme = uri.getScheme();
		if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
			throw new IllegalArgumentException("RSS URL must use http or https");
		}

		String host = uri.getHost();
		if (host == null) {
			throw new IllegalArgumentException("RSS URL must have a valid host");
		}

		final InetAddress[] addresses;
		try {
			addresses = InetAddress.getAllByName(host);
		} catch (Exception e) {
			throw new IllegalArgumentException("RSS URL host could not be resolved");
		}

		for (InetAddress addr : addresses) {
			if (isInternal(addr)) {
				throw new IllegalArgumentException("RSS URL must not target internal networks");
			}
		}
	}

	/** True if the address is loopback, link-local, site-local, any-local, or multicast. */
	public static boolean isInternal(InetAddress addr) {
		return addr.isLoopbackAddress()
				|| addr.isAnyLocalAddress()
				|| addr.isLinkLocalAddress()
				|| addr.isSiteLocalAddress()
				|| addr.isMulticastAddress();
	}
}
