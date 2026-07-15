package rolling_restarts.content.util;

import java.net.Inet6Address;
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
	 *
	 * @return the resolved addresses that were validated, so a caller can pin the connection to
	 *     exactly these addresses (see {@link PinnedDnsResolverProvider}) and close the gap
	 *     between this validation and the later connection re-resolving independently.
	 */
	public static InetAddress[] validatePublicUrl(String url) {
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

		return addresses;
	}

	/**
	 * True if the address is non-public and must not be the target of an outbound fetch:
	 * loopback, any-local, link-local, site-local, multicast, or an IPv6 unique-local address.
	 *
	 * <p>{@link InetAddress#isSiteLocalAddress()} only covers IPv4 RFC 1918 ranges and the
	 * deprecated IPv6 {@code fec0::/10} range — it does NOT match the {@code fc00::/7} unique-local
	 * range (RFC 4193) that Docker/Kubernetes IPv6 networks actually use, so we check that
	 * explicitly. IPv4-mapped IPv6 addresses (e.g. {@code ::ffff:127.0.0.1}) are normalised by the
	 * JDK so the IPv4 predicates above already apply to them.
	 */
	public static boolean isInternal(InetAddress addr) {
		return addr.isLoopbackAddress()
				|| addr.isAnyLocalAddress()
				|| addr.isLinkLocalAddress()
				|| addr.isSiteLocalAddress()
				|| addr.isMulticastAddress()
				|| isUniqueLocalIpv6(addr)
				|| isSharedAddressSpace(addr);
	}

	/** Matches RFC 6598 Shared Address Space 100.64.0.0/10 (Carrier-Grade NAT / cloud VPCs). */
	private static boolean isSharedAddressSpace(InetAddress addr) {
		byte[] bytes = addr.getAddress();
		if (bytes.length != 4) {
			return false;
		}
		return (bytes[0] & 0xFF) == 100 && (bytes[1] & 0xC0) == 64;
	}

	/** Matches the IPv6 unique-local range fc00::/7 (the high 7 bits of the first byte are 1111110). */
	private static boolean isUniqueLocalIpv6(InetAddress addr) {
		if (!(addr instanceof Inet6Address)) {
			return false;
		}
		byte[] bytes = addr.getAddress();
		return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
	}
}
