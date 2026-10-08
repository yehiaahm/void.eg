package com.voidstore.api.order;

import com.voidstore.api.auth.CurrentUser;
import com.voidstore.api.auth.RateLimiter;
import com.voidstore.api.common.ApiException;
import com.voidstore.api.content.SettingsService;
import com.voidstore.api.order.OrderDtos.OrderDto;
import com.voidstore.api.order.OrderDtos.PlaceReq;
import com.voidstore.api.order.OrderDtos.PlaceRes;
import com.voidstore.api.order.OrderDtos.QuoteReq;
import com.voidstore.api.order.OrderDtos.QuoteRes;
import com.voidstore.api.order.OrderDtos.TrackReq;
import com.voidstore.api.returns.CustomerOrders;
import com.voidstore.api.returns.ReturnDtos;
import com.voidstore.api.returns.ReturnService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

	private final OrderService service;
	private final CustomerOrders customerOrders;
	private final ReturnService returns;
	private final RateLimiter limiter;
	private final SettingsService settings;

	public OrderController(OrderService service, CustomerOrders customerOrders, ReturnService returns, RateLimiter limiter,
			SettingsService settings) {
		this.service = service;
		this.customerOrders = customerOrders;
		this.returns = returns;
		this.limiter = limiter;
		this.settings = settings;
	}

	/** Live totals for the cart / checkout (prices, stock, shipping, coupon). */
	@PostMapping("/quote")
	public QuoteRes quote(@Valid @RequestBody QuoteReq req) {
		return service.quote(req);
	}

	@PostMapping
	public PlaceRes place(@Valid @RequestBody PlaceReq req, HttpServletRequest http) {
		limiter.check("order:" + http.getRemoteAddr(), 10, Duration.ofMinutes(10));
		var me = CurrentUser.get();
		// While the owner has the store closed only staff can place (test) orders.
		if (settings.storeClosed() && me.map(p -> !p.role().isStaff()).orElse(true)) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "store_closed", "The store is closed right now");
		}
		return service.place(req, me.map(CurrentUser.Principal::id).orElse(null));
	}

	/** Guest tracking: needs both the order number and the phone used at checkout. */
	@PostMapping("/track")
	@Transactional(readOnly = true)
	public OrderDto track(@Valid @RequestBody TrackReq req, HttpServletRequest http) {
		limiter.check("track:" + http.getRemoteAddr(), 20, Duration.ofMinutes(10));
		return customerOrders.view(customerOrders.forGuest(req.number(), req.phone()));
	}

	/** Guest cancels a not-yet-confirmed order (number + phone). */
	@PostMapping("/cancel")
	public OrderDto cancel(@Valid @RequestBody TrackReq req, HttpServletRequest http) {
		limiter.check("track:" + http.getRemoteAddr(), 20, Duration.ofMinutes(10));
		return customerOrders.guestCancel(req.number(), req.phone());
	}

	/** Guest asks to return / exchange pieces of a delivered order (number + phone). */
	@PostMapping("/returns")
	@Transactional
	public OrderDto requestReturn(@Valid @RequestBody ReturnDtos.GuestCreateReq req, HttpServletRequest http) {
		limiter.check("return:" + http.getRemoteAddr(), 10, Duration.ofMinutes(10));
		var o = customerOrders.forGuest(req.number(), req.phone());
		returns.create(o, req.type(), req.reason(), req.note(), req.items(), o.getName());
		return customerOrders.view(o);
	}
}
