package com.voidstore.api.order;

import com.voidstore.api.catalog.Product;
import com.voidstore.api.catalog.ProductImage;
import com.voidstore.api.catalog.ProductRepository;
import com.voidstore.api.catalog.ProductVariant;
import com.voidstore.api.common.ApiException;
import com.voidstore.api.common.Localized;
import com.voidstore.api.order.OrderDtos.LineReq;
import com.voidstore.api.order.OrderDtos.PlaceReq;
import com.voidstore.api.order.OrderDtos.PlaceRes;
import com.voidstore.api.order.OrderDtos.QuoteLine;
import com.voidstore.api.order.OrderDtos.QuoteRes;
import com.voidstore.api.payment.PaymentService;
import com.voidstore.api.shipping.ShippingZone;
import com.voidstore.api.shipping.ShippingZoneRepository;
import com.voidstore.api.user.Address;
import com.voidstore.api.user.AddressRepository;
import com.voidstore.api.user.User;
import com.voidstore.api.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

	public static final int MAX_QTY_PER_LINE = 10;
	/** Pieces in one order (PV-4): a limited drop cannot be reserved in bulk by one checkout. */
	public static final int MAX_PIECES_PER_ORDER = 10;

	private final ProductRepository products;
	private final ShippingZoneRepository zones;
	private final CouponRepository coupons;
	private final CouponRedemptionRepository redemptions;
	private final OrderRepository orders;
	private final UserRepository users;
	private final AddressRepository addresses;
	private final PaymentService payments;
	private final ApplicationEventPublisher events;
	private final EntityManager em;
	private final OrderLocks locks;
	private final TrackingTokens tokens;
	private final int maxOpenPerCustomer;

	public OrderService(ProductRepository products, ShippingZoneRepository zones,
			CouponRepository coupons, CouponRedemptionRepository redemptions, OrderRepository orders, UserRepository users,
			AddressRepository addresses, PaymentService payments, ApplicationEventPublisher events, EntityManager em,
			OrderLocks locks, TrackingTokens tokens, @Value("${void.orders.max-open-per-customer:2}") int maxOpenPerCustomer) {
		this.locks = locks;
		this.tokens = tokens;
		this.maxOpenPerCustomer = maxOpenPerCustomer;
		this.products = products;
		this.zones = zones;
		this.coupons = coupons;
		this.redemptions = redemptions;
		this.orders = orders;
		this.users = users;
		this.addresses = addresses;
		this.payments = payments;
		this.events = events;
		this.em = em;
	}

	/** Published after commit → emails. */
	/** {@code trackingToken} goes only into the confirmation e-mail link; it is never stored. */
	public record OrderPlaced(long orderId, String trackingToken) {}

	public record StatusChanged(long orderId, Order.Status to) {}

	// ------------------------------------------------------------------ quote

	private record Resolved(LineReq req, Product product, ProductVariant variant, int qty) {}

	/** Merge duplicate (slug, size) lines and resolve them against the catalogue. */
	private List<Resolved> resolve(List<LineReq> lines) {
		Map<String, Integer> merged = new LinkedHashMap<>();
		for (var l : lines) merged.merge(l.slug() + "\u0000" + l.size().toUpperCase(), l.qty(), Integer::sum);
		List<Resolved> out = new ArrayList<>();
		for (var e : merged.entrySet()) {
			String[] k = e.getKey().split("\u0000");
			int qty = Math.min(MAX_QTY_PER_LINE, e.getValue());
			var product = products.findBySlug(k[0]).orElse(null);
			var variant = product == null ? null
					: product.getVariants().stream().filter(v -> v.getSize().equalsIgnoreCase(k[1])).findFirst().orElse(null);
			out.add(new Resolved(new LineReq(k[0], k[1], qty), product, variant, qty));
		}
		return out;
	}

	private static String issue(Resolved r, int stock) {
		if (r.product() == null || r.variant() == null || r.product().getStatus() != Product.Status.ACTIVE) return "unavailable";
		if (r.product().getPrice() == null) return "no_price";
		if (stock <= 0) return "out_of_stock";
		if (r.qty() > stock) return "insufficient_stock";
		return null;
	}

	private static String frontImage(Product p) {
		return p.getImages().stream().filter(i -> "front".equals(i.getKind())).map(ProductImage::getUrl).findFirst()
				.orElse(p.getImages().isEmpty() ? null : p.getImages().getFirst().getUrl());
	}

	private record CouponCheck(Coupon coupon, String error) {}

	/** Who is asking, for the per-customer coupon limit. Every field is optional. */
	record Who(String email, String phone, String addressKey, Long userId) {
		static final Who NONE = new Who(null, null, null, null);
	}

	/**
	 * VOID-03: every reason a code cannot be used is reported as the same generic "invalid", so the response never
	 * tells whether a code exists, is expired, used up or below its minimum.
	 */
	private CouponCheck checkCoupon(Coupon c, int subtotal, Who who) {
		final CouponCheck invalid = new CouponCheck(null, "invalid");
		if (c == null || !c.isActive()) return invalid;
		var now = Instant.now();
		if (c.getStartsAt() != null && now.isBefore(c.getStartsAt())) return invalid;
		if (c.getEndsAt() != null && now.isAfter(c.getEndsAt())) return invalid;
		if (c.getMaxUses() != null && c.getUsedCount() >= c.getMaxUses()) return invalid;
		if (subtotal < c.getMinSubtotal()) return invalid;
		if (c.getPerCustomer() != null && alreadyRedeemed(c, who) >= c.getPerCustomer()) return invalid;
		return new CouponCheck(c, null);
	}

	/** VOID-05: same account, phone, delivery address or (canonical) e-mail all count as the same customer. */
	private long alreadyRedeemed(Coupon c, Who who) {
		String email = who.email() == null || who.email().isBlank() ? null : User.normalizeEmail(who.email());
		String phone = who.phone() == null || who.phone().isBlank() ? null : Phones.normalize(who.phone());
		if (phone != null && phone.isEmpty()) phone = null;
		if (email == null && phone == null && who.addressKey() == null && who.userId() == null) return 0;
		return redemptions.countForCustomer(c.getId(), CustomerIdentity.canonicalEmail(email), email, phone, who.addressKey(),
				who.userId());
	}

	@Transactional(readOnly = true)
	public QuoteRes quote(OrderDtos.QuoteReq req) {
		var resolved = resolve(req.items());
		List<QuoteLine> lines = new ArrayList<>();
		int subtotal = 0;
		for (var r : resolved) {
			int stock = r.variant() == null ? 0 : r.variant().getStock();
			String issue = issue(r, stock);
			Integer unit = r.product() == null ? null : r.product().getPrice();
			int lineTotal = issue == null ? unit * r.qty() : 0;
			subtotal += lineTotal;
			lines.add(new QuoteLine(r.req().slug(), r.req().size(), r.qty(),
					r.product() == null ? null : Localized.of(r.product().getNameEn(), r.product().getNameAr()),
					unit, lineTotal, Math.max(0, Math.min(MAX_QTY_PER_LINE, stock)),
					r.product() == null ? null : frontImage(r.product()), issue));
		}

		String code = Coupon.normalize(req.couponCode());
		CouponCheck cc = code == null || code.isBlank() ? new CouponCheck(null, null)
				: checkCoupon(coupons.findByCode(code).orElse(null), subtotal, new Who(req.email(), req.phone(), null, null));

		Integer shipping = null;
		if (req.governorate() != null && !req.governorate().isBlank()) {
			shipping = zones.findByCode(req.governorate()).filter(ShippingZone::isAvailable).map(ShippingZone::getFee).orElse(null);
		}
		boolean free = cc.coupon() != null && cc.coupon().getType() == Coupon.Type.FREE_SHIPPING;
		if (free && shipping != null) shipping = 0;
		int discount = cc.coupon() == null ? 0 : cc.coupon().discountFor(subtotal);
		Integer total = shipping == null ? null : subtotal - discount + shipping;
		return new QuoteRes(lines, subtotal, discount, shipping, total, cc.coupon() == null ? null : cc.coupon().getCode(),
				cc.error(), free, payments.enabledMethods());
	}

	// ------------------------------------------------------------------ place

	public PlaceRes place(PlaceReq req, Long userId) {
		return place(req, userId, false);
	}

	/** {@code staff}: staff test-orders are exempt from the open-order cap. */
	@Transactional
	public PlaceRes place(PlaceReq req, Long userId, boolean staff) {
		String phone = Phones.requireValid(req.contact().phone());
		String email = User.normalizeEmail(req.contact().email());
		Order.PaymentMethod method;
		try {
			method = Order.PaymentMethod.valueOf(req.paymentMethod().toUpperCase());
		} catch (IllegalArgumentException e) {
			throw ApiException.badRequest("payment_unavailable", "Unknown payment method");
		}
		var provider = payments.require(method);

		var zone = zones.findByCode(req.address().governorate()).filter(ShippingZone::isAvailable)
				.orElseThrow(() -> ApiException.badRequest("shipping_unavailable", "We don't deliver to this governorate yet"));

		// Resolve, then lock the variant rows (ordered by id → no deadlocks) and re-check stock under the lock.
		var resolved = resolve(req.items());

		// PV-4: unconfirmed cash-on-delivery orders reserve stock, so one buyer cannot hold a limited drop hostage.
		if (resolved.stream().mapToInt(Resolved::qty).sum() > MAX_PIECES_PER_ORDER) {
			throw ApiException.badRequest("too_many_items", "An order can contain at most " + MAX_PIECES_PER_ORDER + " pieces");
		}
		if (!staff && (orders.countByPhoneAndStatus(phone, Order.Status.NEW) >= maxOpenPerCustomer
				|| orders.countByEmailAndStatus(email, Order.Status.NEW) >= maxOpenPerCustomer)) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "too_many_open_orders",
					"You already have orders waiting for confirmation");
		}
		var locked = lock(resolved.stream().map(Resolved::variant).filter(Objects::nonNull).toList());

		List<Map<String, Object>> problems = new ArrayList<>();
		int subtotal = 0;
		for (var r : resolved) {
			var v = r.variant() == null ? null : locked.get(r.variant().getId());
			String issue = issue(r, v == null ? 0 : v.getStock());
			if (issue != null) {
				problems.add(Map.of("slug", r.req().slug(), "size", r.req().size(), "issue", issue,
						"available", v == null ? 0 : v.getStock()));
			} else {
				subtotal += v.getProduct().getPrice() * r.qty();
			}
		}
		if (!problems.isEmpty()) throw new StockProblem(problems);

		User user = userId == null ? null : users.findById(userId).orElse(null);
		String addressKey = CustomerIdentity.addressKey(zone.getCode(), req.address().city(), req.address().street(),
				req.address().building());

		Coupon coupon = null;
		String code = Coupon.normalize(req.couponCode());
		if (code != null && !code.isBlank()) {
			var cc = checkCoupon(coupons.lockByCode(code).orElse(null), subtotal,
					new Who(email, phone, addressKey, user == null ? null : user.getId()));
			if (cc.error() != null) throw ApiException.badRequest("coupon_" + cc.error(), "This code can't be used");
			coupon = cc.coupon();
		}

		int discount = coupon == null ? 0 : coupon.discountFor(subtotal);
		int shipping = coupon != null && coupon.getType() == Coupon.Type.FREE_SHIPPING ? 0 : zone.getFee();

		var o = new Order();
		o.setNumber("TMP-" + UUID.randomUUID().toString().substring(0, 12));
		o.setUser(user);
		o.setLang(req.lang());
		o.setEmail(email);
		o.setName(req.contact().name().trim());
		o.setPhone(phone);
		o.setGovernorate(zone.getCode());
		o.setCity(req.address().city().trim());
		o.setStreet(req.address().street().trim());
		o.setBuilding(req.address().building());
		o.setAddressNotes(req.address().notes());
		o.setPin(req.address().pin());
		o.setCustomerNote(req.note());
		o.setWhatsappOptIn(Boolean.TRUE.equals(req.whatsappOptIn()));
		o.setSubtotal(subtotal);
		o.setDiscount(discount);
		o.setShipping(shipping);
		o.setTotal(subtotal - discount + shipping);
		o.setCouponCode(coupon == null ? null : coupon.getCode());
		o.setPaymentMethod(method);

		for (var r : resolved) {
			var v = locked.get(r.variant().getId());
			v.setStock(v.getStock() - r.qty());
			o.getItems().add(new OrderItem(o, v, frontImage(v.getProduct()), r.qty()));
		}
		o.getEvents().add(new OrderEvent(o, OrderEvent.Type.PLACED, null, Order.Status.NEW, "", null));
		orders.saveAndFlush(o);
		o.setNumber(String.format("VOID-%06d", 1000 + o.getId()));

		if (coupon != null) {
			coupon.incrementUsed();
			redemptions.save(new CouponRedemption(coupon, o, CustomerIdentity.canonicalEmail(email), phone,
					user == null ? null : user.getId(), addressKey));
		}

		if (user != null && Boolean.TRUE.equals(req.saveAddress())) saveAddress(user, req, phone, zone.getCode());

		var start = provider.start(o);
		String trackingToken = tokens.forOrder(o.getNumber());
		events.publishEvent(new OrderPlaced(o.getId(), trackingToken));
		return new PlaceRes(o.getNumber(), o.getTotal(), method.name(), start.redirectUrl(), trackingToken);
	}

	private void saveAddress(User user, PlaceReq req, String phone, String governorate) {
		var existing = addresses.findAllByUserIdOrderByIsDefaultDescIdAsc(user.getId());
		boolean same = existing.stream().anyMatch(a -> a.getStreet().equalsIgnoreCase(req.address().street().trim())
				&& a.getGovernorate().equals(governorate));
		if (same) return;
		var a = new Address(user);
		a.setName(req.contact().name().trim());
		a.setPhone(phone);
		a.setGovernorate(governorate);
		a.setCity(req.address().city().trim());
		a.setStreet(req.address().street().trim());
		a.setBuilding(req.address().building());
		a.setNotes(req.address().notes());
		a.setDefault(existing.isEmpty());
		addresses.save(a);
	}

	/** 409 with the list of lines that can't be fulfilled, so the cart can update itself. */
	public static class StockProblem extends ApiException {
		private final List<Map<String, Object>> lines;

		public StockProblem(List<Map<String, Object>> lines) {
			super(HttpStatus.CONFLICT, "stock_changed", "Some items are no longer available in that quantity");
			this.lines = lines;
		}

		public List<Map<String, Object>> getLines() {
			return lines;
		}
	}

	// ------------------------------------------------------------------ lifecycle

	@Transactional
	public Order changeStatus(Order o, Order.Status to, String note, User actor) {
		// PV-1: lock + reload first, so a concurrent cancel/return of the same order is applied exactly once
		locks.lock(o);
		var from = o.getStatus();
		if (from == to) return o;
		if (!from.canMoveTo(to)) {
			throw ApiException.badRequest("invalid_transition", "Can't move an order from " + from + " to " + to);
		}
		if (to.restocks() && !from.restocks()) restock(o);
		o.setStatus(to);
		if (to == Order.Status.DELIVERED && o.getPaymentMethod() == Order.PaymentMethod.COD) {
			o.setPaymentStatus(Order.PaymentStatus.PAID);
		}
		o.getEvents().add(new OrderEvent(o, OrderEvent.Type.STATUS, from, to, note, actor));
		events.publishEvent(new StatusChanged(o.getId(), to));
		return o;
	}

	/**
	 * SELECT ... FOR UPDATE each variant (in id order, so concurrent checkouts can't deadlock) and
	 * refresh it, so the stock we check and decrement is the latest committed value.
	 */
	private Map<Long, ProductVariant> lock(List<ProductVariant> list) {
		Map<Long, ProductVariant> locked = new LinkedHashMap<>();
		list.stream().sorted(Comparator.comparing(ProductVariant::getId)).forEach(v -> {
			if (locked.containsKey(v.getId())) return;
			em.refresh(v, LockModeType.PESSIMISTIC_WRITE);
			locked.put(v.getId(), v);
		});
		return locked;
	}

	private void restock(Order o) {
		var locked = lock(o.getItems().stream().map(OrderItem::getVariant).filter(Objects::nonNull).toList());
		for (var i : o.getItems()) {
			int back = i.getQty() - i.getReturnedQty(); // pieces returned through a return request are already back
			i.addReturned(back);
			if (i.getVariant() == null || back <= 0) continue;
			var v = locked.get(i.getVariant().getId());
			if (v != null) v.setStock(v.getStock() + back);
		}
		if (o.getCouponCode() != null) {
			redemptions.findByOrderId(o.getId()).ifPresent(r -> {
				r.getCoupon().decrementUsed();
				redemptions.delete(r);
			});
		}
	}

	/** Customers may cancel their own order while it hasn't been confirmed yet. */
	@Transactional
	public Order cancelByCustomer(String number, long userId) {
		var o = orders.findByNumber(number).filter(x -> x.getUser() != null && x.getUser().getId() == userId)
				.orElseThrow(() -> ApiException.notFound("Order"));
		locks.lock(o);
		if (o.getStatus() != Order.Status.NEW) {
			throw ApiException.badRequest("cannot_cancel", "This order is already being prepared");
		}
		return changeStatus(o, Order.Status.CANCELLED, "Cancelled by customer", o.getUser());
	}

	/**
	 * PV-4: cancels an order nobody confirmed within {@code hours} (stock goes back on the shelf).
	 * Re-checks under the row lock, so an order staff confirmed a moment ago is left alone.
	 */
	@Transactional
	public boolean autoCancel(long orderId, int hours) {
		var o = orders.findById(orderId).orElse(null);
		if (o == null) return false;
		locks.lock(o);
		if (o.getStatus() != Order.Status.NEW || o.getCreatedAt().isAfter(Instant.now().minus(java.time.Duration.ofHours(hours)))) {
			return false;
		}
		changeStatus(o, Order.Status.CANCELLED, "Auto-cancelled: not confirmed within " + hours + " hours", null);
		return true;
	}

	/** Entry point for online payment webhooks (Paymob). */
	@Transactional
	public void markPaid(String number, String paymentRef) {
		var o = orders.findByNumber(number).orElseThrow(() -> ApiException.notFound("Order"));
		if (o.getPaymentStatus() == Order.PaymentStatus.PAID) return;
		o.setPaymentStatus(Order.PaymentStatus.PAID);
		o.setPaymentRef(paymentRef);
		o.getEvents().add(new OrderEvent(o, OrderEvent.Type.PAYMENT, null, null, "Paid · " + paymentRef, null));
	}
}
