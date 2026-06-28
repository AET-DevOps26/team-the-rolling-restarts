package rolling_restarts.content.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;

class UrlSafetyValidatorTest {

	// --- isInternal classification (uses IP literals, so no DNS lookup happens) ---

	@Test
	void isInternal_blocksLoopbackAnyLinkSiteLocalAndMulticast() throws Exception {
		assertThat(UrlSafetyValidator.isInternal(InetAddress.getByName("127.0.0.1"))).isTrue();   // loopback v4
		assertThat(UrlSafetyValidator.isInternal(InetAddress.getByName("::1"))).isTrue();          // loopback v6
		assertThat(UrlSafetyValidator.isInternal(InetAddress.getByName("0.0.0.0"))).isTrue();      // any-local
		assertThat(UrlSafetyValidator.isInternal(InetAddress.getByName("169.254.169.254"))).isTrue(); // link-local (cloud metadata)
		assertThat(UrlSafetyValidator.isInternal(InetAddress.getByName("10.0.0.1"))).isTrue();     // site-local v4
		assertThat(UrlSafetyValidator.isInternal(InetAddress.getByName("224.0.0.1"))).isTrue();    // multicast
	}

	@Test
	void isInternal_blocksIpv6UniqueLocal() throws Exception {
		// fc00::/7 — what Docker/Kubernetes IPv6 networks use; NOT caught by isSiteLocalAddress().
		assertThat(UrlSafetyValidator.isInternal(InetAddress.getByName("fd00::1"))).isTrue();
		assertThat(UrlSafetyValidator.isInternal(InetAddress.getByName("fc00::1"))).isTrue();
	}

	@Test
	void isInternal_blocksIpv4MappedLoopback() throws Exception {
		// ::ffff:127.0.0.1 is normalised to the IPv4 loopback by the JDK.
		assertThat(UrlSafetyValidator.isInternal(InetAddress.getByName("::ffff:127.0.0.1"))).isTrue();
	}

	@Test
	void isInternal_allowsPublicAddresses() throws Exception {
		assertThat(UrlSafetyValidator.isInternal(InetAddress.getByName("8.8.8.8"))).isFalse();            // public v4
		assertThat(UrlSafetyValidator.isInternal(InetAddress.getByName("2001:4860:4860::8888"))).isFalse(); // public v6
	}

	// --- validatePublicUrl ---

	@Test
	void validatePublicUrl_rejectsNonHttpScheme() {
		assertThatThrownBy(() -> UrlSafetyValidator.validatePublicUrl("ftp://example.com/feed"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void validatePublicUrl_rejectsMissingHost() {
		assertThatThrownBy(() -> UrlSafetyValidator.validatePublicUrl("http:///feed"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void validatePublicUrl_rejectsLoopbackHost() {
		assertThatThrownBy(() -> UrlSafetyValidator.validatePublicUrl("http://127.0.0.1:8081/actuator/env"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("internal");
	}

	@Test
	void validatePublicUrl_rejectsIpv6UniqueLocalHost() {
		// Rejected either as "internal" (host parsed) — the isInternal test pins the fc00::/7
		// classification precisely — never accepted.
		assertThatThrownBy(() -> UrlSafetyValidator.validatePublicUrl("http://[fd00::1]/feed"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void validatePublicUrl_allowsPublicIpLiteral() {
		// 8.8.8.8 is a public literal — no DNS, not internal — so validation passes.
		assertThatCode(() -> UrlSafetyValidator.validatePublicUrl("https://8.8.8.8/feed"))
				.doesNotThrowAnyException();
	}
}
