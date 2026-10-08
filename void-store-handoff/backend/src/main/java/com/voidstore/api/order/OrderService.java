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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

	public static final int MAX_QTY_PER_LINE = 10;

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

	public OrderService(ProductRepository products, ShippingZoneRepository zones,
			CouponRepository coupons, CouponRedemptionRepository redemptions, OrderRepository orders, UserRepository users,
			AddressRepository addresses, PaymentService payments, ApplicationEventPublisher events, EntityManager em) {
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
	public record OrderPlaced(long orderId) {}

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

	private CouponCheck checkCoupon(Coupon c, int subtotal, String email) {
		if (c == null || !c.isActive()) return new CouponCheck(null, "invalid");
		var now = Instant.now();
		if (c.getStartsAt() != null && now.isBefore(c.getStartsAt())) return new CouponCheck(null, "not_started");
		if (c.getEndsAt() != null && now.isAfter(c.getEndsAt())) return new CouponCheck(null, "expired");
		if (c.getMaxUses() != null && c.getUsedCount() >= c.getMaxUses()) return new CouponCheck(null, "used_up");
		if (subtotal < c.getMinSubtotal()) return new CouponCheck(null, "min_subtotal");
		if (c.getPerCustomer() != null && email != null && !email.isBlank()
				&& redemptions.countByCouponIdAndEmail(c.getId(), User.normalizeEmail(email)) >= c.getPerCustomer()) {
			return new CouponCheck(null, "already_used");
		}
		return new CouponCheck(c, null);
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
				: checkCoupon(coupons.findByCode(code).orElse(null), subtotal, req.email());

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

	@Transactional
	public PlaceRes place(PlaceReq req, Long userId) {
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

		Coupon coupon = null;
		String code = Coupon.normalize(req.couponCode());
		if (code != null && !code.isBlank()) {
			var cc = checkCoupon(coupons.lockByCode(code).orElse(null), subtotal, email);
			if (cc.error() != null) throw ApiException.badRequest("coupon_" + cc.error(), "This code can't be used");
			coupon = cc.coupon();
		}

		int discount = coupon == null ? 0 : coupon.discountFor(subtotal);
		int shipping = coupon != null && coupon.getType() == Coupon.Type.FREE_SHIPPING ? 0 : zone.getFee();

		User user = userId == null ? null : users.findById(userId).orElse(null);

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
		o.setCustomerNote(req.note());
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
			redemptions.save(new CouponRedemption(coupon, o, email));
		}

		if (user != null && Boolean.TRUE.equals(req.saveAddress())) saveAddress(user, req, phone, zone.getCode());

		var start = provider.start(o);
		events.publishEvent(new OrderPlaced(o.getId()));
		return new PlaceRes(o.getNumber(), o.getTotal(), method.name(), start.redirectUrl());
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
		if (o.getStatus() != Order.Status.NEW) {
			throw ApiException.badRequest("cannot_cancel", "This order is already being prepared");
		}
		return changeStatus(o, Order.Status.CANCELLED, "Cancelled by customer", o.getUser());
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
