package com.voidstore.api.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.voidstore.api.catalog.Product;
import com.voidstore.api.catalog.ProductRepository;
import com.voidstore.api.catalog.ProductVariant;
import com.voidstore.api.common.ApiException;
import com.voidstore.api.order.OrderDtos.AddressReq;
import com.voidstore.api.order.OrderDtos.ContactReq;
import com.voidstore.api.order.OrderDtos.LineReq;
import com.voidstore.api.order.OrderDtos.PlaceReq;
import com.voidstore.api.order.OrderDtos.QuoteReq;
import com.voidstore.api.payment.PaymentProvider;
import com.voidstore.api.payment.PaymentService;
import com.voidstore.api.shipping.ShippingZone;
import com.voidstore.api.shipping.ShippingZoneRepository;
import com.voidstore.api.user.AddressRepository;
import com.voidstore.api.user.User;
import com.voidstore.api.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Regression tests for VOID-03 (no coupon enumeration), VOID-05 (customer identity), PV-1 (order row lock),
 * PV-4 (caps and auto-cancel). Repositories are mocked: these verify the service logic, not SQL.
 */
class OrderServiceTest {

	private ProductRepository products;
	private ShippingZoneRepository zones;
	private CouponRepository coupons;
	private CouponRedemptionRepository redemptions;
	private OrderRepository orders;
	private UserRepository users;
	private PaymentService payments;
	private ApplicationEventPublisher events;
	private OrderLocks locks;
	private OrderService service;
	private Product tee;
	private ProductVariant teeM;

	@BeforeEach
	void setUp() {
		products = mock(ProductRepository.class);
		zones = mock(ShippingZoneRepository.class);
		coupons = mock(CouponRepository.class);
		redemptions = mock(CouponRedemptionRepository.class);
		orders = mock(OrderRepository.class);
		users = mock(UserRepository.class);
		payments = mock(PaymentService.class);
		events = mock(ApplicationEventPublisher.class);
		locks = mock(OrderLocks.class);
		var tokens = new TrackingTokens(new SecretKeySpec("k9Vt2mQx7LpR4sYw1ZcN8bHf3JdG6aUe0TqXiOvE5yMrB".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		service = new OrderService(products, zones, coupons, redemptions, orders, users, mock(AddressRepository.class), payments,
				events, mock(EntityManager.class), locks, tokens, 2);

		tee = new Product();
		tee.setSlug("tee");
		tee.setStatus(Product.Status.ACTIVE);
		tee.setPrice(1000);
		teeM = new ProductVariant(tee, "M", 0);
		teeM.setStock(5);
		ReflectionTestUtils.setField(teeM, "id", 11L);
		tee.getVariants().add(teeM);
		var teeL = new ProductVariant(tee, "L", 1);
		teeL.setStock(5);
		ReflectionTestUtils.setField(teeL, "id", 12L);
		tee.getVariants().add(teeL);
		when(products.findBySlug("tee")).thenReturn(Optional.of(tee));

		var zone = mock(ShippingZone.class);
		when(zone.isAvailable()).thenReturn(true);
		when(zone.getCode()).thenReturn("cairo");
		when(zone.getFee()).thenReturn(5000);
		when(zones.findByCode("cairo")).thenReturn(Optional.of(zone));
		when(payments.require(Order.PaymentMethod.COD)).thenReturn(mock(PaymentProvider.class));
		when(orders.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
	}

	// ---------------------------------------------------------------- helpers

	private Coupon coupon(Consumer<Coupon> tweak) {
		var c = new Coupon();
		c.setCode("TEN");
		c.setType(Coupon.Type.PERCENT);
		c.setValue(10);
		tweak.accept(c);
		when(coupons.findByCode("TEN")).thenReturn(Optional.of(c));
		when(coupons.lockByCode("TEN")).thenReturn(Optional.of(c));
		return c;
	}

	private String couponErrorFor(Consumer<Coupon> tweak) {
		coupon(tweak);
		return service.quote(new QuoteReq(List.of(new LineReq("tee", "M", 1)), "cairo", "ten", "buyer@example.com", "01001234567"))
				.couponError();
	}

	private static PlaceReq placeReq(String coupon, LineReq... lines) {
		return new PlaceReq(List.of(lines), new ContactReq("Buyer+x@Example.com", "Buyer", "+20 100 123 4567"),
				new AddressReq("cairo", "Nasr City", "1 Test St", "B2", null), null, coupon, "COD", "en", false, null);
	}

	private static Order orderWith(Order.Status status, long id) {
		var o = new Order();
		o.setStatus(status);
		ReflectionTestUtils.setField(o, "id", id);
		o.setNumber("VOID-00" + (1000 + id));
		return o;
	}

	// ---------------------------------------------------------------- VOID-03

	@Test
	void everyReasonACouponCannotBeUsedLooksIdentical() {
		when(coupons.findByCode("TEN")).thenReturn(Optional.empty());
		var missing = service.quote(new QuoteReq(List.of(new LineReq("tee", "M", 1)), "cairo", "TEN", null, null)).couponError();

		String inactive = couponErrorFor(c -> c.setActive(false));
		String expired = couponErrorFor(c -> c.setEndsAt(Instant.now().minus(Duration.ofDays(1))));
		String notStarted = couponErrorFor(c -> c.setStartsAt(Instant.now().plus(Duration.ofDays(1))));
		String usedUp = couponErrorFor(c -> {
			c.setMaxUses(1);
			c.incrementUsed();
		});
		String belowMinimum = couponErrorFor(c -> c.setMinSubtotal(10_000_000));
		when(redemptions.countForCustomer(any(), any(), any(), any(), any(), any())).thenReturn(1L);
		String alreadyUsed = couponErrorFor(c -> c.setPerCustomer(1));

		assertThat(List.of(missing, inactive, expired, notStarted, usedUp, belowMinimum, alreadyUsed)).containsOnly("invalid");
	}

	@Test
	void aUsableCouponStillWorks() {
		assertThat(couponErrorFor(c -> {})).isNull();
		var q = service.quote(new QuoteReq(List.of(new LineReq("tee", "M", 2)), "cairo", "TEN", null, null));
		assertThat(q.discount()).isEqualTo(200);
	}

	@Test
	void placingWithABadCouponReportsOnlyTheGenericCode() {
		coupon(c -> c.setActive(false));
		assertThatThrownBy(() -> service.place(placeReq("TEN", new LineReq("tee", "M", 1)), null))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("coupon_invalid"));
	}

	// ---------------------------------------------------------------- VOID-05

	@Test
	void perCustomerLimitMatchesOnCanonicalEmailPhoneAddressAndAccount() {
		coupon(c -> c.setPerCustomer(1));
		var signedIn = mock(User.class);
		when(signedIn.getId()).thenReturn(77L);
		when(users.findById(77L)).thenReturn(Optional.of(signedIn));
		when(redemptions.countForCustomer(any(), any(), any(), any(), any(), any())).thenReturn(1L);

		assertThatThrownBy(() -> service.place(placeReq("TEN", new LineReq("tee", "M", 1)), 77L))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("coupon_invalid"));

		String addressKey = CustomerIdentity.addressKey("cairo", "Nasr City", "1 Test St", "B2");
		verify(redemptions).countForCustomer(any(), eq("buyer@example.com"), eq("buyer+x@example.com"), eq("01001234567"),
				eq(addressKey), eq(77L));
	}

	@Test
	void aNewEmailAloneNoLongerGivesAFreshAllowance() {
		coupon(c -> c.setPerCustomer(1));
		// the repository reports a prior redemption by the same phone/address even though this e-mail is new
		when(redemptions.countForCustomer(any(), any(), any(), eq("01001234567"), any(), any())).thenReturn(1L);
		assertThatThrownBy(() -> service.place(placeReq("TEN", new LineReq("tee", "M", 1)), null))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("coupon_invalid"));
	}

	// ---------------------------------------------------------------- PV-4

	@Test
	void oneOrderCannotHoldMoreThanTheMaximumNumberOfPieces() {
		assertThatThrownBy(() -> service.place(placeReq(null, new LineReq("tee", "M", 6), new LineReq("tee", "L", 5)), null))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("too_many_items"));
		verify(orders, never()).saveAndFlush(any());
	}

	@Test
	void aCustomerWithTooManyUnconfirmedOrdersIsRefused() {
		when(orders.countByPhoneAndStatus("01001234567", Order.Status.NEW)).thenReturn(2L);
		assertThatThrownBy(() -> service.place(placeReq(null, new LineReq("tee", "M", 1)), null))
				.isInstanceOfSatisfying(ApiException.class, e -> {
					assertThat(e.getCode()).isEqualTo("too_many_open_orders");
					assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
				});
		// same through the e-mail: a new phone number does not help
		when(orders.countByPhoneAndStatus("01001234567", Order.Status.NEW)).thenReturn(0L);
		when(orders.countByEmailAndStatus("buyer+x@example.com", Order.Status.NEW)).thenReturn(2L);
		assertThatThrownBy(() -> service.place(placeReq(null, new LineReq("tee", "M", 1)), null))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("too_many_open_orders"));
	}

	@Test
	void staffTestOrdersAreExemptFromTheOpenOrderCap() {
		when(orders.countByPhoneAndStatus(any(), any())).thenReturn(5L);
		teeM.setStock(0); // stops the flow right after the cap check, with a different error
		assertThatThrownBy(() -> service.place(placeReq(null, new LineReq("tee", "M", 1)), null, true))
				.isInstanceOf(OrderService.StockProblem.class);
		verify(orders, never()).countByPhoneAndStatus(any(), any());
	}

	@Test
	void autoCancelCancelsAnOrderNobodyConfirmed() {
		var o = orderWith(Order.Status.NEW, 5);
		ReflectionTestUtils.setField(o, "createdAt", Instant.now().minus(Duration.ofHours(100)));
		when(orders.findById(5L)).thenReturn(Optional.of(o));

		assertThat(service.autoCancel(5L, 72)).isTrue();
		assertThat(o.getStatus()).isEqualTo(Order.Status.CANCELLED);
		verify(events).publishEvent(any(OrderService.StatusChanged.class));
	}

	@Test
	void autoCancelLeavesFreshOrdersAndOrdersStaffConfirmedMeanwhile() {
		var fresh = orderWith(Order.Status.NEW, 6);
		when(orders.findById(6L)).thenReturn(Optional.of(fresh));
		assertThat(service.autoCancel(6L, 72)).isFalse();
		assertThat(fresh.getStatus()).isEqualTo(Order.Status.NEW);

		// staff confirm the order in another transaction while this one waits for the row lock
		var racing = orderWith(Order.Status.NEW, 7);
		ReflectionTestUtils.setField(racing, "createdAt", Instant.now().minus(Duration.ofHours(100)));
		when(orders.findById(7L)).thenReturn(Optional.of(racing));
		doAnswer(inv -> {
			racing.setStatus(Order.Status.CONFIRMED);
			return racing;
		}).when(locks).lock(racing);
		assertThat(service.autoCancel(7L, 72)).isFalse();
		assertThat(racing.getStatus()).isEqualTo(Order.Status.CONFIRMED);
		verifyNoInteractions(events);
	}

	// ---------------------------------------------------------------- PV-1

	@Test
	void statusChangeTakesTheOrderLockBeforeLookingAtTheStatus() {
		var o = orderWith(Order.Status.NEW, 8);
		service.changeStatus(o, Order.Status.CANCELLED, "x", null);
		var order = inOrder(locks, events);
		order.verify(locks).lock(o);
		order.verify(events).publishEvent(any(OrderService.StatusChanged.class));
		assertThat(o.getStatus()).isEqualTo(Order.Status.CANCELLED);
	}

	@Test
	void aSecondConcurrentCancelSeesTheCommittedStateAndDoesNothing() {
		// Transaction B waits on the row lock; when it gets it, the reload shows transaction A already cancelled the order.
		var o = orderWith(Order.Status.NEW, 9);
		doAnswer(inv -> {
			o.setStatus(Order.Status.CANCELLED);
			return o;
		}).when(locks).lock(o);

		service.changeStatus(o, Order.Status.CANCELLED, "Cancelled by customer", null);

		verifyNoInteractions(events); // no second StatusChanged, and restock() (which would add stock again) never ran
		verify(orders, never()).saveAndFlush(any());
	}

	@Test
	void customerCancelAlsoLocksBeforeCheckingTheStatus() {
		var user = mock(User.class);
		when(user.getId()).thenReturn(3L);
		var o = orderWith(Order.Status.NEW, 10);
		o.setUser(user);
		when(orders.findByNumber(o.getNumber())).thenReturn(Optional.of(o));
		doAnswer(inv -> {
			o.setStatus(Order.Status.CONFIRMED); // staff confirmed it while we waited
			return o;
		}).when(locks).lock(o);
		assertThatThrownBy(() -> service.cancelByCustomer(o.getNumber(), 3L))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("cannot_cancel"));
	}

	@Test
	void unusedStubsDoNotHideNullIdentifiers() {
		// quote without any identifier must not touch the redemption table at all
		coupon(c -> c.setPerCustomer(1));
		service.quote(new QuoteReq(List.of(new LineReq("tee", "M", 1)), "cairo", "TEN", null, null));
		verify(redemptions, never()).countForCustomer(any(), any(), any(), any(), any(), any());
		Mockito.reset(redemptions);
		service.quote(new QuoteReq(List.of(new LineReq("tee", "M", 1)), "cairo", "TEN", "a@example.com", null));
		verify(redemptions).countForCustomer(any(), eq("a@example.com"), eq("a@example.com"), isNull(), isNull(), isNull());
	}
}
