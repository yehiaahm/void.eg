package com.voidstore.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** VOID-02 / PV-2: a client must never be able to choose its own rate-limit identity. */
class ClientIpResolverTest {

	private final ClientIpResolver resolver = new ClientIpResolver(ClientIpResolver.DEFAULT_TRUSTED);

	private static MockHttpServletRequest req(String peer, String... xff) {
		var r = new MockHttpServletRequest("POST", "/api/v1/auth/login");
		r.setRemoteAddr(peer);
		for (String h : xff) r.addHeader("X-Forwarded-For", h);
		return r;
	}

	@Test
	void forwardedHeaderIsIgnoredWhenThePeerIsNotATrustedProxy() {
		// app reached directly from the internet: attacker rotates the header on every request
		assertThat(resolver.resolve(req("198.51.100.20", "1.1.1.1"))).isEqualTo("198.51.100.20");
		assertThat(resolver.resolve(req("198.51.100.20", "2.2.2.2"))).isEqualTo("198.51.100.20");
		assertThat(resolver.resolve(req("198.51.100.20", "10.0.0.1, 3.3.3.3"))).isEqualTo("198.51.100.20");
	}

	@Test
	void trustedProxyOverwritesHeaderWithTheRealClient() {
		assertThat(resolver.resolve(req("172.18.0.5", "203.0.113.9"))).isEqualTo("203.0.113.9");
	}

	@Test
	void valueTheClientPrependedToTheHeaderIsNotUsed() {
		// client sent "X-Forwarded-For: 1.2.3.4"; the proxy appended the address it really saw
		assertThat(resolver.resolve(req("172.18.0.5", "1.2.3.4, 203.0.113.9"))).isEqualTo("203.0.113.9");
		// spoof also sent as a separate header line
		assertThat(resolver.resolve(req("172.18.0.5", "1.2.3.4", "203.0.113.9"))).isEqualTo("203.0.113.9");
	}

	@Test
	void additionalTrustedHopsAreSkipped() {
		assertThat(resolver.resolve(req("172.18.0.5", "198.51.100.77, 203.0.113.9, 10.1.2.3"))).isEqualTo("203.0.113.9");
	}

	@Test
	void garbageInTheHeaderNeverBecomesTheIdentity() {
		assertThat(resolver.resolve(req("172.18.0.5", "not-an-ip"))).isEqualTo("172.18.0.5");
		assertThat(resolver.resolve(req("172.18.0.5", "evil.example.com, 203.0.113.9"))).isEqualTo("203.0.113.9");
		assertThat(resolver.resolve(req("172.18.0.5", "203.0.113.9, \"; drop table"))).isEqualTo("172.18.0.5");
		assertThat(resolver.resolve(req("172.18.0.5", ""))).isEqualTo("172.18.0.5");
	}

	@Test
	void missingHeaderFromTrustedPeerFallsBackToThePeer() {
		assertThat(resolver.resolve(req("172.18.0.5"))).isEqualTo("172.18.0.5");
	}

	@Test
	void ipv6ClientsShareOneBucketPerSlash64() {
		String a = resolver.resolve(req("172.18.0.5", "2001:db8:abcd:12:1111:2222:3333:4444"));
		String b = resolver.resolve(req("172.18.0.5", "2001:db8:abcd:12:ffff:eeee:dddd:cccc"));
		String other = resolver.resolve(req("172.18.0.5", "2001:db8:abcd:13::1"));
		assertThat(a).isEqualTo(b).isEqualTo("2001:0db8:abcd:0012::/64");
		assertThat(other).isNotEqualTo(a);
	}

	@Test
	void customTrustedProxyListIsHonoured() {
		var strict = new ClientIpResolver("172.18.0.5/32");
		assertThat(strict.resolve(req("172.18.0.5", "203.0.113.9"))).isEqualTo("203.0.113.9");
		// another private host is NOT trusted under the strict list
		assertThat(strict.resolve(req("172.18.0.99", "203.0.113.9"))).isEqualTo("172.18.0.99");
	}
}
