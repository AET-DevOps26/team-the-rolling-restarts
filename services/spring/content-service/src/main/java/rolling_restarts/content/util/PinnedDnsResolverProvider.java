package rolling_restarts.content.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * JVM-wide DNS resolver override, scoped in effect to whichever hostname is currently pinned on
 * the calling thread. Used by {@link rolling_restarts.content.service.RssFetcherService} to close
 * a DNS-rebinding TOCTOU gap: {@link UrlSafetyValidator} validates a hostname's resolved
 * addresses, but without this, the actual HTTP connection re-resolves independently moments
 * later — a malicious/compromised DNS server could serve a public address for the validation
 * lookup and an internal one for the connect lookup. Pinning forces the connect-time lookup to
 * reuse the exact addresses that were validated.
 *
 * <p>Every hostname without an active pin (and every other thread) falls through unchanged to the
 * JDK's builtin resolver, so this is safe alongside MongoDB driver / OTLP exporter DNS needs.
 * Registered as the system-wide resolver via
 * {@code META-INF/services/java.net.spi.InetAddressResolverProvider} (JEP 418); only one such
 * provider can be effective per JVM, so a future dependency shouldn't register its own.
 */
public final class PinnedDnsResolverProvider extends InetAddressResolverProvider {

	private static final ThreadLocal<Map<String, List<InetAddress>>> PINS =
			ThreadLocal.withInitial(ConcurrentHashMap::new);

	public static void pin(String host, List<InetAddress> addresses) {
		PINS.get().put(host.toLowerCase(Locale.ROOT), addresses);
	}

	public static void unpin(String host) {
		PINS.get().remove(host.toLowerCase(Locale.ROOT));
	}

	@Override
	public InetAddressResolver get(InetAddressResolverProvider.Configuration configuration) {
		InetAddressResolver builtin = configuration.builtinResolver();
		return new InetAddressResolver() {
			@Override
			public Stream<InetAddress> lookupByName(String host, LookupPolicy lookupPolicy)
					throws UnknownHostException {
				List<InetAddress> pinned = PINS.get().get(host.toLowerCase(Locale.ROOT));
				return pinned != null ? pinned.stream() : builtin.lookupByName(host, lookupPolicy);
			}

			@Override
			public String lookupByAddress(byte[] addr) throws UnknownHostException {
				return builtin.lookupByAddress(addr);
			}
		};
	}

	@Override
	public String name() {
		return "rolling-restarts-pinned-dns";
	}
}
