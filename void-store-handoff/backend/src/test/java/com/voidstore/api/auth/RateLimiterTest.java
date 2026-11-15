package com.voidstore.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.voidstore.api.common.ApiException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RateLimiterTest {

	private static final Duration W = Duration.ofMinutes(10);
	private final ClientIpResolver ips = new ClientIpResolver(ClientIpResolver.DEFAULT_TRUSTED);
	private final RateLimiter limiter = new RateLimiter(true, ips, new AuditLog(ips));

	@Test
	void checkCountsEveryCallAndThrows429AtTheLimit() {
		for (int i = 0; i < 3; i++) limiter.check("t-ip:1.1.1.1", 3, W);
		assertThatThrownBy(() -> limiter.check("t-ip:1.1.1.1", 3, W))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
		assertThatCode(() -> limiter.check("t-ip:2.2.2.2", 3, W)).doesNotThrowAnyException(); // other subject unaffected
	}

	@Test
	void ensureBelowDoesNotCountButRecordedHitsDo() {
		for (int i = 0; i < 20; i++) limiter.ensureBelow("t-acct:a@b.c", 2, W); // read-only, never trips
		limiter.record("t-acct:a@b.c", W);
		limiter.ensureBelow("t-acct:a@b.c", 2, W);
		limiter.record("t-acct:a@b.c", W);
		assertThatThrownBy(() -> limiter.ensureBelow("t-acct:a@b.c", 2, W)).isInstanceOf(ApiException.class);
	}

	@Test
	void resetClearsTheBucket() {
		limiter.record("t-acct:x", W);
		limiter.record("t-acct:x", W);
		limiter.reset("t-acct:x");
		assertThatCode(() -> limiter.ensureBelow("t-acct:x", 2, W)).doesNotThrowAnyException();
	}

	@Test
	void disabledLimiterNeverThrows() {
		var off = new RateLimiter(false, ips, new AuditLog(ips));
		for (int i = 0; i < 50; i++) off.check("t-ip:1", 1, W);
	}

	@Test
	void mapIsBoundedAndFailsClosedInsteadOfGrowingForever() {
		for (int i = 0; i < RateLimiter.MAX_KEYS; i++) limiter.record("flood:" + i, W);
		assertThat(limiter.size()).isEqualTo(RateLimiter.MAX_KEYS);
		assertThatThrownBy(() -> limiter.record("flood:one-more", W)).isInstanceOf(ApiException.class);
		assertThat(limiter.size()).isEqualTo(RateLimiter.MAX_KEYS);
		// existing keys keep working
		assertThatCode(() -> limiter.record("flood:5", W)).doesNotThrowAnyException();
	}
}
