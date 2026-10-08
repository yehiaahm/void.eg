package com.voidstore.api.auth;

import com.voidstore.api.common.ApiException;
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
 * Small in-memory sliding-window limiter for login, registration, password reset,
 * order placement and order tracking. Good enough for a single instance; swap for Redis
 * if the API is ever scaled horizontally.
 */
@Component
public class RateLimiter {

	private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
	private final boolean enabled;

	public RateLimiter(@Value("${void.rate-limit-enabled:true}") boolean enabled) {
		this.enabled = enabled;
	}

	/** Throws 429 when {@code key} was seen more than {@code max} times within {@code window}. */
	public void check(String key, int max, Duration window) {
		if (!enabled) return;
		var now = Instant.now();
		var q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
		synchronized (q) {
			while (!q.isEmpty() && q.peekFirst().isBefore(now.minus(window))) q.pollFirst();
			if (q.size() >= max) {
				throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "Too many attempts, try again later");
			}
			q.addLast(now);
		}
	}

	public void reset(String key) {
		hits.remove(key);
	}

	@Scheduled(fixedDelay = 10 * 60 * 1000)
	void cleanup() {
		var cutoff = Instant.now().minus(Duration.ofHours(1));
		hits.entrySet().removeIf(e -> {
			synchronized (e.getValue()) {
				return e.getValue().isEmpty() || e.getValue().peekLast().isBefore(cutoff);
			}
		});
	}
}
