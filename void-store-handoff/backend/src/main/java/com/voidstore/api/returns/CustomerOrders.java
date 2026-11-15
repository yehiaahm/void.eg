package com.voidstore.api.returns;

import com.voidstore.api.auth.AuditLog;
import com.voidstore.api.common.ApiException;
import com.voidstore.api.order.Order;
import com.voidstore.api.order.OrderDtos;
import com.voidstore.api.order.OrderDtos.OrderDto;
import com.voidstore.api.order.OrderLocks;
import com.voidstore.api.order.OrderRepository;
import com.voidstore.api.order.OrderService;
import com.voidstore.api.order.Phones;
import com.voidstore.api.order.TrackingTokens;
import com.voidstore.api.returns.ReturnDtos.ReturnDto;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the customer-facing order view (with returns) and resolves orders for guests.
 *
 * <p>Guest access (VOID-04): the private tracking token from the e-mailed link is the only thing that authorises
 * the full view and the actions (cancel, return). The order number + phone pair only unlocks a masked, read-only
 * status view, because order numbers are sequential and phone numbers are not secret.
 */
@Service
public class CustomerOrders {

	private final OrderRepository orders;
	private final ReturnRepository returns;
	private final ReturnService returnService;
	private final OrderService orderService;
	private final OrderLocks locks;
	private final TrackingTokens tokens;
	private final AuditLog audit;

	public CustomerOrders(OrderRepository orders, ReturnRepository returns, ReturnService returnService,
			OrderService orderService, OrderLocks locks, TrackingTokens tokens, AuditLog audit) {
		this.orders = orders;
		this.returns = returns;
		this.returnService = returnService;
		this.orderService = orderService;
		this.locks = locks;
		this.tokens = tokens;
		this.audit = audit;
	}

	public OrderDto view(Order o) {
		var list = returns.findAllByOrderIdOrderByCreatedAtDesc(o.getId());
		var until = returnService.returnUntil(o).filter(u -> u.isAfter(java.time.Instant.now())).orElse(null);
		var left = returnService.returnable(o, list);
		List<ReturnDto> dtos = list.stream().map(ReturnDtos::of).toList();
		return OrderDtos.of(o, left, until, o.getStatus() == Order.Status.NEW, dtos);
	}

	/** View for a guest: full when they proved ownership with the token, masked and read-only otherwise. */
	public OrderDto guestView(Order o, boolean full) {
		return OrderDtos.forGuest(view(o), full);
	}

	public static String normalizeNumber(String number) {
		return number == null ? "" : number.trim().toUpperCase(Locale.ROOT);
	}

	/** Strong proof: the order number and its private token. */
	public Order forGuestToken(String number, String token) {
		String n = normalizeNumber(number);
		return orders.findByNumber(n).filter(o -> tokens.matches(o.getNumber(), token))
				.orElseThrow(() -> ApiException.notFound("Order"));
	}

	/** Weak proof (read-only, masked view only): the order number and the phone used at checkout. */
	public Order forGuestPhone(String number, String phone) {
		String p = Phones.normalize(phone);
		return orders.findByNumber(normalizeNumber(number)).filter(o -> o.getPhone().equals(p))
				.orElseThrow(() -> ApiException.notFound("Order"));
	}

	@Transactional
	public OrderDto guestCancel(String number, String token) {
		var o = locks.lock(forGuestToken(number, token));
		if (o.getStatus() != Order.Status.NEW) {
			throw ApiException.badRequest("cannot_cancel", "This order is already being prepared");
		}
		orderService.changeStatus(o, Order.Status.CANCELLED, "Cancelled by customer", null);
		audit.info("order.cancelled.guest", "order", o.getNumber());
		return guestView(o, true);
	}
}
