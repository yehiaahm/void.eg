package com.voidstore.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

/**
 * Works out the real client address for rate limiting and audit logs (VOID-02 / PV-2).
 *
 * <p>The application never trusts {@code X-Forwarded-For} on its own. The header is read only when the TCP peer is
 * one of the configured trusted proxies ({@code void.trusted-proxies}, CIDR list; default = loopback and private
 * networks, i.e. the Caddy container). The chain is then walked from the right (the entry added by the nearest
 * proxy), skipping trusted proxies; the first untrusted address is the client. A value a client prepends to the
 * header is therefore never used, and a request that reaches the app directly from the internet ignores the header
 * completely.
 *
 * <p>IPv6 clients are bucketed by /64 so a user with a whole /64 cannot rotate addresses to dodge limits.
 */
@Component
public class ClientIpResolver {

	public static final String DEFAULT_TRUSTED =
			"127.0.0.0/8,::1/128,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,169.254.0.0/16,fc00::/7,fe80::/10";

	private static final Pattern IPV4 = Pattern.compile("(\\d{1,3})(\\.\\d{1,3}){3}");
	private static final Pattern IPV6 = Pattern.compile("[0-9a-fA-F:.]{2,45}");

	private final List<IpAddressMatcher> trusted = new ArrayList<>();

	public ClientIpResolver(@Value("${void.trusted-proxies:" + DEFAULT_TRUSTED + "}") String trustedProxies) {
		for (String c : trustedProxies.split("\\s*,\\s*")) {
			if (!c.isBlank()) trusted.add(new IpAddressMatcher(c.trim()));
		}
	}

	/** Address used as the rate-limit / audit identity of the caller. */
	public String resolve(HttpServletRequest req) {
		String peer = normalize(req.getRemoteAddr());
		if (peer == null) return "unknown";
		if (!isTrusted(peer)) return bucket(peer);

		List<String> chain = forwardedChain(req);
		for (int i = chain.size() - 1; i >= 0; i--) {
			String hop = chain.get(i);
			if (!isTrusted(hop)) return bucket(hop);
		}
		return bucket(peer);
	}

	private boolean isTrusted(String ip) {
		for (var m : trusted) {
			try {
				if (m.matches(ip)) return true;
			} catch (RuntimeException e) {
				// malformed address → not trusted
			}
		}
		return false;
	}

	/** All X-Forwarded-For entries, in order, with invalid (non-IP) entries dropped. */
	private static List<String> forwardedChain(HttpServletRequest req) {
		List<String> out = new ArrayList<>();
		Enumeration<String> headers = req.getHeaders("X-Forwarded-For");
		while (headers != null && headers.hasMoreElements()) {
			for (String part : headers.nextElement().split(",")) {
				String ip = normalize(part.trim());
				if (ip == null) {
					// an unparseable entry breaks the chain of custody: discard everything before it
					out.clear();
				} else {
					out.add(ip);
				}
			}
		}
		return out;
	}

	/** Returns a canonical literal IP or null. Never performs a DNS lookup. */
	static String normalize(String raw) {
		if (raw == null) return null;
		String s = raw.trim();
		if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length() - 1);
		if (s.isEmpty()) return null;
		boolean v4 = IPV4.matcher(s).matches();
		boolean v6 = !v4 && s.indexOf(':') >= 0 && IPV6.matcher(s).matches();
		if (!v4 && !v6) return null;
		try {
			return InetAddress.getByName(s).getHostAddress();
		} catch (UnknownHostException e) {
			return null;
		}
	}

	/** IPv4 as is; IPv6 reduced to its /64 prefix. */
	static String bucket(String ip) {
		if (ip.indexOf(':') < 0) return ip;
		try {
			byte[] b = InetAddress.getByName(ip).getAddress();
			if (b.length != 16) return ip;
			// IPv4-mapped (::ffff:a.b.c.d) is returned as IPv4 by InetAddress; native IPv6 keeps 16 bytes
			byte[] prefix = Arrays.copyOf(b, 8);
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 8; i += 2) {
				if (i > 0) sb.append(':');
				sb.append(String.format("%02x%02x", prefix[i], prefix[i + 1]));
			}
			return sb.append("::/64").toString();
		} catch (UnknownHostException e) {
			return ip;
		}
	}
}
