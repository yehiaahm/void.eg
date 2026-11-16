package com.voidstore.api.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.voidstore.api.auth.AuditLog;
import com.voidstore.api.auth.ClientIpResolver;
import com.voidstore.api.auth.RateLimiter;
import com.voidstore.api.common.ApiException;
import com.voidstore.api.content.SettingsService;
import com.voidstore.api.order.OrderDtos.LineReq;
import com.voidstore.api.order.OrderDtos.OrderDto;
import com.voidstore.api.order.OrderDtos.QuoteReq;
import com.voidstore.api.order.OrderDtos.QuoteRes;
import com.voidstore.api.order.OrderDtos.TrackReq;
import com.voidstore.api.returns.CustomerOrders;
import com.voidstore.api.returns.ReturnService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

/** VOID-03 (quote limits, coupon enumeration) and VOID-04 (guest tracking / cancel / return authorisation). */
class OrderControllerTest {

	private OrderService service;	private CustomerOrders customerOrders;
	private OrderController controller;

	@BeforeEach
	void setUp() {
		service = mock(OrderService.class);
		customerOrders = mock(CustomerOrders.class);
		var ips = new ClientIpResolver(ClientIpResolver.DEFAULT_TRUSTED);
		controller = new OrderController(service, customerOrders, mock(ReturnService.class),
				new RateLimiter(true, ips, new AuditLog(ips)), mock(SettingsService.class), new AuditLog(ips));
	}

	private static MockHttpServletRequest from(String ip) {
		var r = new MockHttpServletRequest("POST", "/api/v1/orders/quote");
		r.setRemoteAddr(ip);
		return r;
	}

	private static QuoteReq quote(String coupon) {
		return new QuoteReq(List.of(new LineReq("tee", "M", 1)), "cairo", coupon, "a@example.com", null);
	}

	private static QuoteRes res(String couponError) {
		return new QuoteRes(List.of(), 0, 0, null, null, null, couponError, false, List.of());
	}

	private static int statusOf(Runnable r) {
		try {
			r.run();
			return 200;
		} catch (ApiException e) {
			return e.getStatus().value();
		}
	}

	// ---------------------------------------------------------------- VOID-03

	@Test
	void quoteIsRateLimitedPerAddress() {
		when(service.quote(any())).thenReturn(res(null));
		int ok = 0;
		for (int i = 0; i < OrderController.QUOTE_PER_IP + 30; i++) {
			if (statusOf(() -> controller.quote(quote(null), from("198.51.100.1"))) == 200) ok++;
		}
		assertThat(ok).isEqualTo(OrderController.QUOTE_PER_IP);
		assertThat(statusOf(() -> controller.quote(quote(null), from("198.51.100.2")))).isEqualTo(200); // other client unaffected
	}

	@Test
	void guessingCouponCodesIsStoppedAfterAFewFailures() {
		when(service.quote(any())).thenReturn(res("invalid"));
		for (int i = 0; i < OrderController.COUPON_FAILURES_PER_IP; i++) {
			assertThat(statusOf(() -> controller.quote(quote("GUESS" + System.nanoTime()), from("198.51.100.1")))).isEqualTo(200);
		}
		// the next guess is refused before it even reaches the coupon lookup
		assertThat(statusOf(() -> controller.quote(quote("ANOTHER"), from("198.51.100.1")))).isEqualTo(429);
		verify(service, times(OrderController.COUPON_FAILURES_PER_IP)).quote(any());
	}

	@Test
	void validCouponsAndPlainQuotesNeverConsumeTheGuessBudget() {
		when(service.quote(any())).thenReturn(res(null));
		for (int i = 0; i < 50; i++) controller.quote(quote("TEN"), from("198.51.100.1")); // cart refreshes with a good code
		when(service.quote(any())).thenReturn(res("invalid"));
		assertThat(statusOf(() -> controller.quote(quote("OOPS"), from("198.51.100.1")))).isEqualTo(200);
		// a quote without any coupon is not blocked by earlier coupon failures
		for (int i = 0; i < OrderController.COUPON_FAILURES_PER_IP; i++) controller.quote(quote("BAD" + i), from("198.51.100.9"));
		when(service.quote(any())).thenReturn(res(null));
		assertThat(statusOf(() -> controller.quote(quote(null), from("198.51.100.9")))).isEqualTo(200);
	}

	@Test
	void quoteRequestFieldsAreLengthLimited() {
		var v = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
		assertThat(v.validate(quote("X".repeat(41)))).isNotEmpty();
		assertThat(v.validate(new QuoteReq(List.of(new LineReq("tee", "M", 1)), "cairo", null, "a".repeat(200) + "@example.com", null)))
				.isNotEmpty();
		assertThat(v.validate(new LineReq("s".repeat(121), "M", 1))).isNotEmpty();
		assertThat(v.validate(quote("TEN"))).isEmpty();
	}

	// ---------------------------------------------------------------- VOID-04

	@Test
	void trackWithTheTokenReturnsTheFullViewAndIsNotLockedByPhoneGuessing() {
		var order = mock(Order.class);
		when(customerOrders.forGuestPhone(anyString(), anyString())).thenThrow(ApiException.notFound("Order"));
		when(customerOrders.forGuestToken("VOID-001001", "good-token")).thenReturn(order);
		var dto = mock(OrderDto.class);
		when(customerOrders.guestView(order, true)).thenReturn(dto);

		// attacker burns the phone budget of that order from many addresses
		for (int i = 0; i < OrderController.PHONE_FAILURES_PER_ORDER; i++) {
			assertThat(statusOf(() -> controller.track(new TrackReq("void-001001", "01111111111", null), from("203.0.113." + (1 + System.nanoTime() % 200)))))
					.isEqualTo(404);
		}
		assertThat(statusOf(() -> controller.track(new TrackReq("VOID-001001", "01111111111", null), from("203.0.113.250")))).isEqualTo(429);
		// the real customer with the e-mailed link still gets in
		assertThat(controller.track(new TrackReq("VOID-001001", null, "good-token"), from("203.0.113.251"))).isSameAs(dto);
	}

	@Test
	void phoneOnlyTrackingReturnsTheMaskedViewNeverTheFullOne() {
		var order = mock(Order.class);
		when(customerOrders.forGuestPhone("VOID-001001", "01001234567")).thenReturn(order);
		var masked = mock(OrderDto.class);
		when(customerOrders.guestView(order, false)).thenReturn(masked);
		assertThat(controller.track(new TrackReq("VOID-001001", "01001234567", null), from("203.0.113.1"))).isSameAs(masked);
		verify(customerOrders, never()).guestView(order, true);
	}

	@Test
	void trackWithNoProofIsRejected() {
		assertThatThrownBy(() -> controller.track(new TrackReq("VOID-001001", " ", null), from("203.0.113.1")))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("proof_required"));
	}

	@Test
	void wrongTokenIsAnIndistinguishableNotFound() {
		when(customerOrders.forGuestToken(anyString(), anyString())).thenThrow(ApiException.notFound("Order"));
		assertThatThrownBy(() -> controller.track(new TrackReq("VOID-001001", null, "nope"), from("203.0.113.1")))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
	}

	@Test
	void cancelNeedsTheTokenAPhoneIsNotEnough() {
		assertThatThrownBy(() -> controller.cancel(new TrackReq("VOID-001001", "01001234567", null), from("203.0.113.1")))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("proof_required"));
		verify(customerOrders, never()).guestCancel(anyString(), anyString());
		assertThatCode(() -> controller.cancel(new TrackReq("VOID-001001", null, "good-token"), from("203.0.113.1")))
				.doesNotThrowAnyException();
		verify(customerOrders).guestCancel("VOID-001001", "good-token");
	}

	@Test
	void theBatchLookupHasItsOwnBudgetAndDoesNotUseUpTheTrackOne() {
		when(customerOrders.summariesForTokens(any())).thenReturn(List.of());
		var req = new OrderDtos.TrackBatchReq(List.of(new OrderDtos.BatchEntry("VOID-001001", "t")));
		int blocked = 0;
		for (int i = 0; i < OrderController.BATCH_PER_IP + 10; i++) {
			if (statusOf(() -> controller.trackBatch(req, from("198.51.100.90"))) == 429) blocked++;
		}
		assertThat(blocked).isEqualTo(10);
		// the same address can still use the e-mail-link path
		when(customerOrders.forGuestToken(anyString(), anyString())).thenReturn(mock(Order.class));
		assertThat(statusOf(() -> controller.track(new TrackReq("VOID-001001", null, "t"), from("198.51.100.90")))).isEqualTo(200);
	}

	@Test
	void trackAndCancelShareAnAddressBudget() {
		when(customerOrders.forGuestToken(anyString(), anyString())).thenThrow(ApiException.notFound("Order"));
		int blocked = 0;
		for (int i = 0; i < 40; i++) {
			if (statusOf(() -> controller.track(new TrackReq("VOID-00" + System.nanoTime() % 1000, null, "guess"), from("198.51.100.77"))) == 429) blocked++;
		}
		assertThat(blocked).isEqualTo(20); // 20 allowed per 10 minutes, the rest refused
	}
}
