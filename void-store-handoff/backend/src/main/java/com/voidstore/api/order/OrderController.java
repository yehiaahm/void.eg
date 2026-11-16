package com.voidstore.api.order;

import com.voidstore.api.auth.AuditLog;
import com.voidstore.api.auth.CurrentUser;
import com.voidstore.api.auth.RateLimiter;
import com.voidstore.api.common.ApiException;
import com.voidstore.api.content.SettingsService;
import com.voidstore.api.order.OrderDtos.OrderDto;
import com.voidstore.api.order.OrderDtos.OrderSummaryDto;
import com.voidstore.api.order.OrderDtos.PlaceReq;
import com.voidstore.api.order.OrderDtos.PlaceRes;
import com.voidstore.api.order.OrderDtos.QuoteReq;
import com.voidstore.api.order.OrderDtos.QuoteRes;
import com.voidstore.api.order.OrderDtos.TrackBatchReq;
import com.voidstore.api.order.OrderDtos.TrackReq;
import com.voidstore.api.returns.CustomerOrders;
import com.voidstore.api.returns.ReturnDtos;
import com.voidstore.api.returns.ReturnService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

	static final int QUOTE_PER_IP = 120;
	static final Duration QUOTE_WINDOW = Duration.ofMinutes(5);
	/** Invalid coupon codes per client address (VOID-03): a code cannot be guessed at speed. */
	static final int COUPON_FAILURES_PER_IP = 10;
	static final Duration COUPON_WINDOW = Duration.ofMinutes(15);
	/** Wrong phone guesses per order number (VOID-04); the token path is not affected, so nobody can lock a customer out. */
	static final int PHONE_FAILURES_PER_ORDER = 8;
	static final Duration PHONE_WINDOW = Duration.ofHours(1);
	/** Calls of the "my orders" batch lookup per client address; a page load costs one. */
	static final int BATCH_PER_IP = 60;
	static final Duration BATCH_WINDOW = Duration.ofMinutes(10);

	private final OrderService service;
	private final CustomerOrders customerOrders;
	private final ReturnService returns;
	private final RateLimiter limiter;
	private final SettingsService settings;
	private final AuditLog audit;

	public OrderController(OrderService service, CustomerOrders customerOrders, ReturnService returns, RateLimiter limiter,
			SettingsService settings, AuditLog audit) {
		this.service = service;
		this.customerOrders = customerOrders;
		this.returns = returns;
		this.limiter = limiter;
		this.settings = settings;
		this.audit = audit;
	}

	/** Live totals for the cart / checkout (prices, stock, shipping, coupon). */
	@PostMapping("/quote")
	public QuoteRes quote(@Valid @RequestBody QuoteReq req, HttpServletRequest http) {
		String ip = limiter.ip(http);
		limiter.check("quote-ip:" + ip, QUOTE_PER_IP, QUOTE_WINDOW);
		boolean coupon = req.couponCode() != null && !req.couponCode().isBlank();
		if (coupon) limiter.ensureBelow("coupon-fail-ip:" + ip, COUPON_FAILURES_PER_IP, COUPON_WINDOW);
		var res = service.quote(req);
		if (coupon && res.couponError() != null) limiter.record("coupon-fail-ip:" + ip, COUPON_WINDOW);
		return res;
	}

	@PostMapping
	public PlaceRes place(@Valid @RequestBody PlaceReq req, HttpServletRequest http) {
		String ip = limiter.ip(http);
		limiter.check("order-ip:" + ip, 10, Duration.ofMinutes(10));
		boolean coupon = req.couponCode() != null && !req.couponCode().isBlank();
		if (coupon) limiter.ensureBelow("coupon-fail-ip:" + ip, COUPON_FAILURES_PER_IP, COUPON_WINDOW);
		var me = CurrentUser.get();
		boolean staff = me.map(p -> p.role().isStaff()).orElse(false);
		// While the owner has the store closed only staff can place (test) orders.
		if (settings.storeClosed() && !staff) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "store_closed", "The store is closed right now");
		}
		try {
			return service.place(req, me.map(CurrentUser.Principal::id).orElse(null), staff);
		} catch (ApiException e) {
			if (coupon && e.getCode().startsWith("coupon_")) limiter.record("coupon-fail-ip:" + ip, COUPON_WINDOW);
			throw e;
		}
	}

	/**
	 * Guest tracking. {@code token} (the e-mailed link) returns the order with phone/e-mail masked and the right to
	 * cancel or return; {@code phone} alone returns only a masked, read-only status view (VOID-04).
	 */
	@PostMapping("/track")
	@Transactional(readOnly = true)
	public OrderDto track(@Valid @RequestBody TrackReq req, HttpServletRequest http) {
		limiter.check("track-ip:" + limiter.ip(http), 20, Duration.ofMinutes(10));
		String number = CustomerOrders.normalizeNumber(req.number());
		if (hasText(req.token())) {
			try {
				return customerOrders.guestView(customerOrders.forGuestToken(number, req.token()), true);
			} catch (ApiException e) {
				audit.warn("order.track.bad_token", "order", number);
				throw e;
			}
		}
		if (!hasText(req.phone())) throw ApiException.badRequest("proof_required", "Use the link from your confirmation e-mail");
		limiter.ensureBelow("track-phone:" + number, PHONE_FAILURES_PER_ORDER, PHONE_WINDOW);
		try {
			return customerOrders.guestView(customerOrders.forGuestPhone(number, req.phone()), false);
		} catch (ApiException e) {
			limiter.record("track-phone:" + number, PHONE_WINDOW);
			audit.warn("order.track.bad_phone", "order", number);
			throw e;
		}
	}

	/**
	 * Status of every order a device remembers, in one call. Token-only (no phone path), summaries only, and
	 * unmatched entries are left out of the answer. It has its own address budget so the home strip can't use up
	 * the one {@link #track} and {@link #cancel} share.
	 */
	@PostMapping("/track-batch")
	@Transactional(readOnly = true)
	public List<OrderSummaryDto> trackBatch(@Valid @RequestBody TrackBatchReq req, HttpServletRequest http) {
		limiter.check("track-batch-ip:" + limiter.ip(http), BATCH_PER_IP, BATCH_WINDOW);
		return customerOrders.summariesForTokens(req.orders());
	}

	/** Guest cancels a not-yet-confirmed order: needs the private token, not just the phone. */
	@PostMapping("/cancel")
	public OrderDto cancel(@Valid @RequestBody TrackReq req, HttpServletRequest http) {
		limiter.check("track-ip:" + limiter.ip(http), 20, Duration.ofMinutes(10));
		if (!hasText(req.token())) throw ApiException.badRequest("proof_required", "Use the link from your confirmation e-mail");
		return customerOrders.guestCancel(req.number(), req.token());
	}

	/** Guest asks to return / exchange pieces of a delivered order: needs the private token. */
	@PostMapping("/returns")
	@Transactional
	public OrderDto requestReturn(@Valid @RequestBody ReturnDtos.GuestCreateReq req, HttpServletRequest http) {
		limiter.check("return-ip:" + limiter.ip(http), 10, Duration.ofMinutes(10));
		var o = customerOrders.forGuestToken(req.number(), req.token());
		returns.create(o, req.type(), req.reason(), req.note(), req.items(), o.getName());
		audit.info("order.return.requested.guest", "order", o.getNumber());
		return customerOrders.guestView(o, true);
	}

	private static boolean hasText(String s) {
		return s != null && !s.isBlank();
	}
}
