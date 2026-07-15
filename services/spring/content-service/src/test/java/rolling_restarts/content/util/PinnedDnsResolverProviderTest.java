package rolling_restarts.content.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Confirms the {@link PinnedDnsResolverProvider} SPI registration actually takes effect JVM-wide
 * (via {@code InetAddress.getAllByName}) and that pinning/unpinning behave as designed. Proving
 * that {@code HttpClient} itself consults the pin during a real connection isn't practically
 * testable without a real or mock server; this test plus the JDK's documented SPI contract is the
 * intended stopping point (see RssFetcherService.fetchWithSameHostRedirect).
 *
 * <p>Each test uses its own unique fake hostname: the JDK's {@code InetAddress} positive-resolution
 * cache sits above the resolver SPI and applies regardless of which resolver answered, so reusing
 * one hostname across tests that pin different addresses milliseconds apart would read back a
 * stale cached answer instead of re-consulting the resolver — a test-only artifact (real fetch
 * cycles in production are minutes/hours apart, well past the cache's default TTL).
 */
class PinnedDnsResolverProviderTest {

	@Test
	void pin_makesGetAllByNameReturnThePinnedAddress() throws Exception {
		String host = "pin-returns-address.invalid";
		// A constructed address (no DNS lookup involved) for a hostname that doesn't resolve on
		// its own — if the pin isn't consulted, this would throw UnknownHostException instead.
		InetAddress pinned = InetAddress.getByAddress(host, new byte[] {1, 2, 3, 4});
		PinnedDnsResolverProvider.pin(host, List.of(pinned));
		try {
			InetAddress[] resolved = InetAddress.getAllByName(host);
			assertThat(resolved).containsExactly(pinned);
		} finally {
			PinnedDnsResolverProvider.unpin(host);
		}
	}

	@Test
	void pin_isCaseInsensitiveOnHostname() throws Exception {
		String host = "pin-case-insensitive.invalid";
		InetAddress pinned = InetAddress.getByAddress(host, new byte[] {5, 6, 7, 8});
		PinnedDnsResolverProvider.pin(host.toUpperCase(java.util.Locale.ROOT), List.of(pinned));
		try {
			InetAddress[] resolved = InetAddress.getAllByName(host);
			assertThat(resolved).containsExactly(pinned);
		} finally {
			PinnedDnsResolverProvider.unpin(host);
		}
	}

	@Test
	void unpin_restoresNormalResolutionFailure() throws Exception {
		String host = "pin-then-unpin.invalid";
		InetAddress pinned = InetAddress.getByAddress(host, new byte[] {9, 9, 9, 9});
		PinnedDnsResolverProvider.pin(host, List.of(pinned));
		PinnedDnsResolverProvider.unpin(host);

		// No pin left, and this hostname isn't real, so normal resolution fails — proves unpin()
		// actually removes the pin rather than leaving it active indefinitely.
		assertThatThrownBy(() -> InetAddress.getAllByName(host))
				.isInstanceOf(UnknownHostException.class);
	}

	@Test
	void unpinnedHostname_stillResolvesNormally() throws Exception {
		// "localhost" was never pinned — falls straight through to the builtin resolver.
		InetAddress[] resolved = InetAddress.getAllByName("localhost");
		assertThat(resolved).isNotEmpty();
	}
}
