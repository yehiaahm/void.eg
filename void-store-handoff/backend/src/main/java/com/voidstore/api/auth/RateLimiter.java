package com.voidstore.api.auth;

import com.voidstore.api.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-memory sliding-window limiter. Good enough for a single instance; swap for Redis if the API is ever scaled
 * horizontally.
 *
 * <p>Key convention: {@code <category>:<subject>} where the category has no colon (for example {@code login-ip},
 * {@code login-acct}); only the category is written to the audit log, never the subject.
 *
 * <p>Hardening (VOID-02 / PV-3): the map is bounded ({@link #MAX_KEYS}) and fails closed when full, windows are
 * capped at one hour, and callers identify clients through {@link ClientIpResolver}, never the raw
 * {@code X-Forwarded-For} header.
 */
@Component
public class RateLimiter {

	static final int MAX_KEYS = 100_000;
	private static final Duration MAX_WINDOW = Duration.ofHours(1);
	private static final int MAX_HITS_PER_KEY = 500;

	private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
	private final boolean enabled;
	private final ClientIpResolver ips;
	private final AuditLog audit;

	public RateLimiter(@Value("${void.rate-limit-enabled:true}") boolean enabled, ClientIpResolver ips, AuditLog audit) {
		this.enabled = enabled;
		this.ips = ips;
		this.audit = audit;
	}

	/** The caller's identity for limiting: the trusted-proxy-aware client address (IPv6 → /64). */
	public String ip(HttpServletRequest req) {
		return ips.resolve(req);
	}

	/** Counts this call and throws 429 when {@code key} was seen more than {@code max} times within {@code window}. */
	public void check(String key, int max, Duration window) {
		if (!enabled) return;
		var now = Instant.now();
		var q = queue(key);
		synchronized (q) {
			trim(q, now, window);
			if (q.size() >= max) throw tooMany(key);
			q.addLast(now);
		}
	}

	/** Throws 429 when {@code key} already has {@code max} recorded hits in {@code window}; does not count this call. */
	public void ensureBelow(String key, int max, Duration window) {
		if (!enabled) return;
		var q = hits.get(key);
		if (q == null) return;
		synchronized (q) {
			trim(q, Instant.now(), window);
			if (q.size() >= max) throw tooMany(key);
		}
	}

	/** Records one hit (for example a failed login) without throwing. */
	public void record(String key, Duration window) {
		if (!enabled) return;
		var now = Instant.now();
		var q = queue(key);
		synchronized (q) {
			trim(q, now, window);
			if (q.size() < MAX_HITS_PER_KEY) q.addLast(now);
		}
	}

	public void reset(String key) {
		hits.remove(key);
	}

	int size() {
		return hits.size();
	}

	private Deque<Instant> queue(String key) {
		var existing = hits.get(key);
		if (existing != null) return existing;
		if (hits.size() >= MAX_KEYS) {
			cleanup();
			if (hits.size() >= MAX_KEYS) throw tooMany(key); // fail closed rather than grow without bound
		}
		return hits.computeIfAbsent(key, k -> new ArrayDeque<>());
	}

	private static void trim(Deque<Instant> q, Instant now, Duration window) {
		var cutoff = now.minus(window.compareTo(MAX_WINDOW) > 0 ? MAX_WINDOW : window);
		while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) q.pollFirst();
	}

	private ApiException tooMany(String key) {
		int i = key.indexOf(':');
		audit.warn("rate_limit.exceeded", "bucket", i < 0 ? key : key.substring(0, i));
		return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "Too many attempts, try again later");
	}

	@Scheduled(fixedDelay = 10 * 60 * 1000)
	void cleanup() {
		var cutoff = Instant.now().minus(MAX_WINDOW);
		hits.entrySet().removeIf(e -> {
			synchronized (e.getValue()) {
				return e.getValue().isEmpty() || e.getValue().peekLast().isBefore(cutoff);
			}
		});
	}
}
