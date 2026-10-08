package com.voidstore.api.returns;

import com.voidstore.api.common.ApiException;
import com.voidstore.api.order.Order;
import com.voidstore.api.order.OrderDtos;
import com.voidstore.api.order.OrderDtos.OrderDto;
import com.voidstore.api.order.OrderRepository;
import com.voidstore.api.order.OrderService;
import com.voidstore.api.order.Phones;
import com.voidstore.api.returns.ReturnDtos.ReturnDto;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the customer-facing order view (with returns) and resolves orders for guests (number + phone). */
@Service
public class CustomerOrders {

	private final OrderRepository orders;
	private final ReturnRepository returns;
	private final ReturnService returnService;
	private final OrderService orderService;

	public CustomerOrders(OrderRepository orders, ReturnRepository returns, ReturnService returnService, OrderService orderService) {
		this.orders = orders;
		this.returns = returns;
		this.returnService = returnService;
		this.orderService = orderService;
	}

	public OrderDto view(Order o) {
		var list = returns.findAllByOrderIdOrderByCreatedAtDesc(o.getId());
		var until = returnService.returnUntil(o).filter(u -> u.isAfter(java.time.Instant.now())).orElse(null);
		var left = returnService.returnable(o, list);
		List<ReturnDto> dtos = list.stream().map(ReturnDtos::of).toList();
		return OrderDtos.of(o, left, until, o.getStatus() == Order.Status.NEW, dtos);
	}

	/** Guest access: the order number and the checkout phone must both match. */
	public Order forGuest(String number, String phone) {
		String p = Phones.normalize(phone);
		return orders.findByNumber(number.trim().toUpperCase()).filter(o -> o.getPhone().equals(p))
				.orElseThrow(() -> ApiException.notFound("Order"));
	}

	@Transactional
	public OrderDto guestCancel(String number, String phone) {
		var o = forGuest(number, phone);
		if (o.getStatus() != Order.Status.NEW) {
			throw ApiException.badRequest("cannot_cancel", "This order is already being prepared");
		}
		orderService.changeStatus(o, Order.Status.CANCELLED, "Cancelled by customer", null);
		return view(o);
	}
}
