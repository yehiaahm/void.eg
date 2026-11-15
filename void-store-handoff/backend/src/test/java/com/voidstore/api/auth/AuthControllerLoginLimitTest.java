package com.voidstore.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.voidstore.api.common.ApiException;
import com.voidstore.api.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

/** VOID-02 regression tests: layered login throttling that cannot be bypassed by rotating e-mails or headers. */
class AuthControllerLoginLimitTest {

	private AuthService auth;
	private AuthController controller;

	@BeforeEach
	void setUp() {
		auth = mock(AuthService.class);
		when(auth.login(anyString(), anyString()))
				.thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "bad_credentials", "Wrong email or password"));
		var ips = new ClientIpResolver(ClientIpResolver.DEFAULT_TRUSTED);
		var limiter = new RateLimiter(true, ips, new AuditLog(ips));
		var props = new AppProperties("http://localhost", "uploads", "x", new AppProperties.Jwt("irrelevant", 15, 30, false), null);
		controller = new AuthController(auth, limiter, props);
	}

	private static MockHttpServletRequest direct(String ip) {
		var r = new MockHttpServletRequest("POST", "/api/v1/auth/login");
		r.setRemoteAddr(ip);
		return r;
	}

	private static MockHttpServletRequest viaProxy(String forwardedFor) {
		var r = new MockHttpServletRequest("POST", "/api/v1/auth/login");
		r.setRemoteAddr("172.18.0.5");
		r.addHeader("X-Forwarded-For", forwardedFor);
		return r;
	}

	private int status(String email, MockHttpServletRequest req) {
		try {
			controller.login(new AuthController.LoginReq(email, "wrong-password"), req);
			return 200;
		} catch (ApiException e) {
			return e.getStatus().value();
		}
	}

	@Test
	void changingTheEmailDoesNotResetTheBudgetOfOneAddress() {
		// password spraying: one source, a new e-mail on every request
		int blockedAt = -1;
		for (int i = 1; i <= 100; i++) {
			if (status("victim" + i + "@example.com", direct("198.51.100.20")) == 429) {
				blockedAt = i;
				break;
			}
		}
		assertThat(blockedAt).as("request number at which the source gets 429").isEqualTo(AuthController.LOGIN_IP_MAX + 1);
		// the password check (bcrypt) is not reached any more once blocked
		verify(auth, times(AuthController.LOGIN_IP_MAX)).login(anyString(), anyString());
	}

	@Test
	void distributedAttackOnOneAccountIsStoppedAcrossAddresses() {
		String target = "owner@example.com";
		for (int i = 1; i <= AuthController.LOGIN_ACCOUNT_MAX_FAILURES; i++) {
			assertThat(status(target, direct("203.0.113." + i))).isEqualTo(401); // 10 different IPs, 1 try each
		}
		// the 11th attempt comes from a fresh address and is blocked on the account, before any password check
		assertThat(status(target, direct("203.0.113.200"))).isEqualTo(429);
		verify(auth, times(AuthController.LOGIN_ACCOUNT_MAX_FAILURES)).login(anyString(), anyString());
		// a different account from that fresh address is unaffected
		assertThat(status("someone-else@example.com", direct("203.0.113.200"))).isEqualTo(401);
	}

	@Test
	void accountKeyIsCaseAndWhitespaceInsensitive() {
		for (int i = 0; i < AuthController.LOGIN_ACCOUNT_MAX_FAILURES; i++) {
			status(i % 2 == 0 ? "Owner@Example.com" : " owner@example.com ", direct("203.0.113." + (10 + i)));
		}
		assertThat(status("OWNER@EXAMPLE.COM", direct("203.0.113.99"))).isEqualTo(429);
	}

	@Test
	void spoofedForwardedForFromAnUntrustedPeerDoesNotGiveAFreshBudget() {
		int evaluated = 0;
		for (int i = 0; i < AuthController.LOGIN_IP_MAX + 20; i++) {
			var r = direct("198.51.100.20");
			r.addHeader("X-Forwarded-For", "9.9." + (i / 250) + "." + (i % 250)); // new fake client on every request
			if (status("user" + i + "@example.com", r) != 429) evaluated++;
		}
		assertThat(evaluated).isEqualTo(AuthController.LOGIN_IP_MAX);
	}

	@Test
	void spoofedHeaderPrependedBehindTheProxyDoesNotGiveAFreshBudget() {
		int evaluated = 0;
		for (int i = 0; i < AuthController.LOGIN_IP_MAX + 20; i++) {
			var r = viaProxy("7.7." + (i / 250) + "." + (i % 250) + ", 203.0.113.9"); // fake first, real last
			if (status("user" + i + "@example.com", r) != 429) evaluated++;
		}
		assertThat(evaluated).isEqualTo(AuthController.LOGIN_IP_MAX);
	}

	@Test
	void differentRealClientsBehindTheProxyKeepSeparateBudgets() {
		for (int i = 0; i < AuthController.LOGIN_IP_MAX; i++) status("u" + i + "@example.com", viaProxy("203.0.113.9"));
		assertThat(status("u-next@example.com", viaProxy("203.0.113.9"))).isEqualTo(429);
		assertThat(status("u-next@example.com", viaProxy("203.0.113.10"))).isEqualTo(401);
	}

	@Test
	void tenthFailureIsStillEvaluatedAndTheEleventhAttemptIsBlocked() {
		for (int i = 0; i < AuthController.LOGIN_ACCOUNT_MAX_FAILURES - 1; i++) status("a@example.com", direct("203.0.113.1"));
		assertThat(status("a@example.com", direct("203.0.113.2"))).isEqualTo(401);
		assertThat(status("a@example.com", direct("203.0.113.3"))).isEqualTo(429);
	}

	@Test
	void disabledAccountResponsesAreNotCountedAsPasswordFailures() {
		doThrow(new ApiException(HttpStatus.FORBIDDEN, "disabled", "x")).when(auth).login(anyString(), anyString());
		for (int i = 0; i < 15; i++) assertThat(status("gone@example.com", direct("203.0.113." + i))).isEqualTo(403);
	}

	@Test
	void oversizedAuthFieldsAreRejectedByValidation() {
		var v = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
		assertThat(v.validate(new AuthController.LoginReq("a".repeat(200) + "@example.com", "pw"))).isNotEmpty();
		assertThat(v.validate(new AuthController.LoginReq("a@example.com", "p".repeat(101)))).isNotEmpty();
		assertThat(v.validate(new AuthController.LoginReq("a@example.com", "p".repeat(100)))).isEmpty();
		assertThat(v.validate(new AuthController.ResetReq("t".repeat(129), "long-enough-password"))).isNotEmpty();
		assertThat(v.validate(new AuthController.ForgotReq("a@example.com", "x".repeat(6)))).isNotEmpty();
	}

	@Test
	void forgotPasswordIsLimitedPerMailboxAcrossAddresses() {
		int accepted = 0;
		for (int i = 0; i < 10; i++) {
			try {
				controller.forgot(new AuthController.ForgotReq("victim@example.com", "en"), direct("203.0.113." + (50 + i)));
				accepted++;
			} catch (ApiException e) {
				assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
			}
		}
		assertThat(accepted).isEqualTo(3);
		verify(auth, never()).login(anyString(), anyString());
	}
}
