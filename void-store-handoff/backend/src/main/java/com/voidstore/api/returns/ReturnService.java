package com.voidstore.api.returns;

import com.voidstore.api.catalog.ProductVariant;
import com.voidstore.api.common.ApiException;
import com.voidstore.api.content.SettingsService;
import com.voidstore.api.order.Order;
import com.voidstore.api.order.OrderEvent;
import com.voidstore.api.order.OrderItem;
import com.voidstore.api.returns.ReturnDtos.ItemReq;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rules for return / exchange requests:
 * - only for delivered orders, within {@code return_window_days} of delivery;
 * - a refund (RETURN) only when the reason is our mistake ({@link ReturnRequest.Reason#ourMistake()});
 * - never more pieces than were bought, minus pieces already returned or in another open request;
 * - an EXCHANGE reserves the replacement size when approved (fails if it's out of stock);
 * - returned pieces go back to stock when the request is marked RECEIVED.
 */
@Service
public class ReturnService {

	private final ReturnRepository returns;
	private final SettingsService settings;
	private final EntityManager em;
	private final ApplicationEventPublisher events;

	public ReturnService(ReturnRepository returns, SettingsService settings, EntityManager em, ApplicationEventPublisher events) {
		this.returns = returns;
		this.settings = settings;
		this.em = em;
		this.events = events;
	}

	public record Created(long requestId) {}

	public record StatusChanged(long requestId, ReturnRequest.Status to) {}

	// ------------------------------------------------------------------ policy

	public Optional<Instant> deliveredAt(Order o) {
		return o.getEvents().stream()
				.filter(e -> e.getType() == OrderEvent.Type.STATUS && e.getToStatus() == Order.Status.DELIVERED)
				.map(OrderEvent::getCreatedAt).max(Comparator.naturalOrder());
	}

	/** Last moment a request can be made, or empty when the order isn't eligible. */
	public Optional<Instant> returnUntil(Order o) {
		if (o.getStatus() != Order.Status.DELIVERED) return Optional.empty();
		int days = settings.integer("return_window_days", 7);
		if (days <= 0) return Optional.empty();
		return deliveredAt(o).map(d -> d.plus(Duration.ofDays(days)));
	}

	/** Pieces of each order line that can still be requested. */
	public Map<Long, Integer> returnable(Order o, List<ReturnRequest> existing) {
		Map<Long, Integer> left = new HashMap<>();
		for (OrderItem i : o.getItems()) left.put(i.getId(), i.getQty() - i.getReturnedQty());
		for (var r : existing) {
			// RECEIVED/COMPLETED pieces are already counted in returnedQty
			if (r.getStatus() == ReturnRequest.Status.REQUESTED || r.getStatus() == ReturnRequest.Status.APPROVED) {
				r.getItems().forEach(ri -> left.merge(ri.getOrderItem().getId(), -ri.getQty(), Integer::sum));
			}
		}
		left.replaceAll((k, v) -> Math.max(0, v));
		return left;
	}

	// ------------------------------------------------------------------ create

	@Transactional
	public ReturnRequest create(Order o, String type, String reason, String note, List<ItemReq> items, String actorName) {
		var until = returnUntil(o).orElseThrow(() -> ApiException.badRequest("not_returnable",
				"Returns and exchanges are available once the order is delivered"));
		if (Instant.now().isAfter(until)) throw ApiException.badRequest("return_window_over", "The return window for this order has ended");

		ReturnRequest.Type t = parse(ReturnRequest.Type.class, type, "bad_type");
		ReturnRequest.Reason why = parse(ReturnRequest.Reason.class, reason, "bad_reason");
		if (t == ReturnRequest.Type.RETURN && !why.ourMistake()) {
			throw ApiException.badRequest("refund_not_allowed",
					"Refunds are only for a wrong, defective or not-as-described piece — choose an exchange instead");
		}

		var existing = returns.findAllByOrderIdOrderByCreatedAtDesc(o.getId());
		var left = returnable(o, existing);
		var req = new ReturnRequest(o, t, why, note);
		Map<Long, Integer> asked = new HashMap<>();
		for (var it : items) {
			var oi = o.getItems().stream().filter(x -> x.getId().equals(it.itemId())).findFirst()
					.orElseThrow(() -> ApiException.badRequest("bad_item", "Item is not part of this order"));
			int total = asked.merge(oi.getId(), it.qty(), Integer::sum);
			if (total > left.getOrDefault(oi.getId(), 0)) {
				throw ApiException.badRequest("qty_too_high", "You can't return more pieces than you received");
			}
			String size = null;
			if (t == ReturnRequest.Type.EXCHANGE) {
				size = it.exchangeSize() == null ? "" : it.exchangeSize().trim().toUpperCase();
				var product = oi.getProduct();
				String wanted = size;
				if (product == null || product.getVariants().stream().noneMatch(v -> v.getSize().equals(wanted))) {
					throw ApiException.badRequest("bad_size", "Choose an available size to exchange for");
				}
			}
			req.getItems().add(new ReturnItem(req, oi, it.qty(), size));
		}
		req.setNumber("TMP-" + java.util.UUID.randomUUID().toString().substring(0, 12));
		req.getEvents().add(new ReturnEvent(req, null, ReturnRequest.Status.REQUESTED, "", actorName));
		returns.saveAndFlush(req);
		req.setNumber(String.format("RET-%05d", 100 + req.getId()));
		o.getEvents().add(new OrderEvent(o, OrderEvent.Type.NOTE, null, null,
				(t == ReturnRequest.Type.EXCHANGE ? "Exchange" : "Return") + " requested · " + req.getNumber(), null));
		events.publishEvent(new Created(req.getId()));
		return req;
	}

	// ------------------------------------------------------------------ lifecycle

	@Transactional
	public ReturnRequest changeStatus(ReturnRequest r, ReturnRequest.Status to, String note, Integer refundAmount, String actorName) {
		var from = r.getStatus();
		if (from == to) return r;
		if (!from.canMoveTo(to)) throw ApiException.badRequest("invalid_transition", "Can't move a request from " + from + " to " + to);

		if (to == ReturnRequest.Status.APPROVED && r.getType() == ReturnRequest.Type.EXCHANGE) reserveExchangeSizes(r);
		if (to == ReturnRequest.Status.RECEIVED) restockReturned(r);
		if (to == ReturnRequest.Status.COMPLETED && r.getType() == ReturnRequest.Type.RETURN) {
			int amount = refundAmount != null ? refundAmount : r.itemsValue();
			if (amount < 0) throw ApiException.badRequest("bad_amount", "Refund can't be negative");
			r.setRefundAmount(amount);
			var o = r.getOrder();
			boolean allBack = o.getItems().stream().allMatch(i -> i.getReturnedQty() >= i.getQty());
			if (allBack && o.getStatus() == Order.Status.DELIVERED) {
				// stock is already back, so mark the order without a second restock
				o.setStatus(Order.Status.RETURNED);
				o.setPaymentStatus(Order.PaymentStatus.REFUNDED);
				o.getEvents().add(new OrderEvent(o, OrderEvent.Type.STATUS, Order.Status.DELIVERED, Order.Status.RETURNED,
						"Fully returned · " + r.getNumber(), null));
			}
		}
		r.setStatus(to);
		r.getEvents().add(new ReturnEvent(r, from, to, note, actorName));
		events.publishEvent(new StatusChanged(r.getId(), to));
		return r;
	}

	private void reserveExchangeSizes(ReturnRequest r) {
		Map<ProductVariant, Integer> need = new HashMap<>();
		for (var ri : r.getItems()) {
			var product = ri.getOrderItem().getProduct();
			var v = product == null ? null : product.getVariants().stream()
					.filter(x -> x.getSize().equals(ri.getExchangeSize())).findFirst().orElse(null);
			if (v == null) throw ApiException.badRequest("bad_size", "Exchange size no longer exists");
			need.merge(v, ri.getQty(), Integer::sum);
		}
		need.keySet().stream().sorted(Comparator.comparing(ProductVariant::getId))
				.forEach(v -> em.refresh(v, LockModeType.PESSIMISTIC_WRITE));
		need.forEach((v, q) -> {
			if (v.getStock() < q) {
				throw ApiException.conflict("exchange_out_of_stock",
						v.getProduct().getNameEn() + " " + v.getSize() + " has only " + v.getStock() + " left");
			}
		});
		need.forEach((v, q) -> v.setStock(v.getStock() - q));
	}

	private void restockReturned(ReturnRequest r) {
		var variants = r.getItems().stream().map(ri -> ri.getOrderItem().getVariant()).filter(java.util.Objects::nonNull)
				.distinct().sorted(Comparator.comparing(ProductVariant::getId)).toList();
		variants.forEach(v -> em.refresh(v, LockModeType.PESSIMISTIC_WRITE));
		for (var ri : r.getItems()) {
			var oi = ri.getOrderItem();
			int canTake = oi.getQty() - oi.getReturnedQty();
			int n = Math.min(canTake, ri.getQty());
			if (n <= 0) continue;
			oi.addReturned(n);
			if (oi.getVariant() != null) oi.getVariant().setStock(oi.getVariant().getStock() + n);
		}
	}

	private static <E extends Enum<E>> E parse(Class<E> type, String value, String code) {
		try {
			return Enum.valueOf(type, value.trim().toUpperCase());
		} catch (RuntimeException e) {
			throw ApiException.badRequest(code, "Unknown value " + value);
		}
	}
}
