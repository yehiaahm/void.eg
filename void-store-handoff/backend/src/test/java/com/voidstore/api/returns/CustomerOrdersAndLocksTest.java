package com.voidstore.api.returns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.voidstore.api.auth.AuditLog;
import com.voidstore.api.auth.ClientIpResolver;
import com.voidstore.api.common.ApiException;
import com.voidstore.api.content.SettingsService;
import com.voidstore.api.order.Order;
import com.voidstore.api.order.OrderDtos;
import com.voidstore.api.order.OrderLocks;
import com.voidstore.api.order.OrderRepository;
import com.voidstore.api.order.OrderService;
import com.voidstore.api.order.TrackingTokens;
import com.voidstore.api.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** VOID-04 guest authorisation and PV-1 locking in the return flow. */
class CustomerOrdersAndLocksTest {

	private OrderRepository orders;
	private OrderService orderService;
	private OrderLocks locks;
	private TrackingTokens tokens;
	private CustomerOrders customerOrders;
	private Order order;

	@BeforeEach
	void setUp() {
		orders = mock(OrderRepository.class);
		orderService = mock(OrderService.class);
		locks = mock(OrderLocks.class);
		when(locks.lock(any())).thenAnswer(inv -> inv.getArgument(0));
		tokens = new TrackingTokens(new SecretKeySpec("k9Vt2mQx7LpR4sYw1ZcN8bHf3JdG6aUe0TqXiOvE5yMrB".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		customerOrders = new CustomerOrders(orders, mock(ReturnRepository.class), mock(ReturnService.class), orderService, locks,
				tokens, new AuditLog(new ClientIpResolver(ClientIpResolver.DEFAULT_TRUSTED)));
		order = new Order();
		ReflectionTestUtils.setField(order, "id", 1L);
		order.setNumber("VOID-001001");
		order.setPhone("01001234567");
		order.setName("Mona Ahmed");
		order.setEmail("mona@example.com");
		when(orders.findByNumber("VOID-001001")).thenReturn(Optional.of(order));
	}

	@Test
	void theTokenOpensTheOrderAndNothingElseDoes() {
		String token = tokens.forOrder("VOID-001001");
		assertThat(customerOrders.forGuestToken(" void-001001 ", token)).isSameAs(order); // number is normalised
		for (String bad : new String[] {"01001234567", "VOID-001001", "", "x", tokens.forOrder("VOID-001002")}) {
			assertThatThrownBy(() -> customerOrders.forGuestToken("VOID-001001", bad)).isInstanceOf(ApiException.class);
		}
	}

	@Test
	void theBatchLookupOnlyAnswersForEntriesWithAValidToken() {
		var other = new Order();
		ReflectionTestUtils.setField(other, "id", 2L);
		other.setNumber("VOID-001002");
		when(orders.findByNumber("VOID-001002")).thenReturn(Optional.of(other));
		String good = tokens.forOrder("VOID-001001");
		var res = customerOrders.summariesForTokens(java.util.List.of(
				new OrderDtos.BatchEntry(" void-001001 ", good), // normalised
				new OrderDtos.BatchEntry("VOID-001001", good), // duplicate is answered once
				new OrderDtos.BatchEntry("VOID-001002", good), // token of another order
				new OrderDtos.BatchEntry("VOID-001002", "01001234567"), // a phone is not a token
				new OrderDtos.BatchEntry("VOID-009999", good))); // unknown order
		assertThat(res).extracting(OrderDtos.OrderSummaryDto::number).containsExactly("VOID-001001");
	}

	@Test
	void claimingNeedsTheTokenAndNeverTakesAnOrderFromAnotherAccount() {
		var me = new User("me@example.com", "x", "Me", User.Role.CUSTOMER);
		ReflectionTestUtils.setField(me, "id", 7L);
		var other = new User("other@example.com", "x", "Other", User.Role.CUSTOMER);
		ReflectionTestUtils.setField(other, "id", 8L);
		var taken = new Order();
		ReflectionTestUtils.setField(taken, "id", 2L);
		taken.setNumber("VOID-001002");
		taken.setUser(other);
		when(orders.findByNumber("VOID-001002")).thenReturn(Optional.of(taken));
		var entries = java.util.List.of(
				new OrderDtos.BatchEntry("VOID-001001", "01001234567"), // a phone is not proof
				new OrderDtos.BatchEntry("VOID-001002", tokens.forOrder("VOID-001002")), // valid token, but already someone's
				new OrderDtos.BatchEntry("VOID-009999", tokens.forOrder("VOID-009999"))); // unknown order
		assertThat(customerOrders.claim(me, entries)).isEmpty();
		assertThat(order.getUser()).isNull();
		assertThat(taken.getUser()).isSameAs(other);

		var res = customerOrders.claim(me, java.util.List.of(
				new OrderDtos.BatchEntry(" void-001001 ", tokens.forOrder("VOID-001001")),
				new OrderDtos.BatchEntry("VOID-001001", tokens.forOrder("VOID-001001")))); // duplicate counted once
		assertThat(res).containsExactly("VOID-001001");
		assertThat(order.getUser()).isSameAs(me);
		verify(locks).lock(order); // locked before it was read and changed

		// asking again is harmless, and another account can't take it afterwards
		assertThat(customerOrders.claim(me, java.util.List.of(new OrderDtos.BatchEntry("VOID-001001", tokens.forOrder("VOID-001001"))))).containsExactly("VOID-001001");
		assertThat(customerOrders.claim(other, java.util.List.of(new OrderDtos.BatchEntry("VOID-001001", tokens.forOrder("VOID-001001"))))).isEmpty();
		assertThat(order.getUser()).isSameAs(me);
	}

	@Test
	void thePhoneOnlyOpensTheMaskedPath() {
		assertThat(customerOrders.forGuestPhone("VOID-001001", "+20 100 123 4567")).isSameAs(order);
		assertThatThrownBy(() -> customerOrders.forGuestPhone("VOID-001001", "01111111111")).isInstanceOf(ApiException.class);
		var view = customerOrders.guestView(order, false);
		assertThat(view.phone()).doesNotContain("1234");
		assertThat(view.email()).doesNotContain("mona@");
		assertThat(view.canCancel()).isFalse();
	}

	@Test
	void cancelRequiresTheTokenAndTakesTheLockFirst() {
		assertThatThrownBy(() -> customerOrders.guestCancel("VOID-001001", "01001234567")).isInstanceOf(ApiException.class);
		verify(orderService, never()).changeStatus(any(), any(), any(), any());

		var view = customerOrders.guestCancel("VOID-001001", tokens.forOrder("VOID-001001"));
		var seq = inOrder(locks, orderService);
		seq.verify(locks).lock(order);
		seq.verify(orderService).changeStatus(order, Order.Status.CANCELLED, "Cancelled by customer", null);
		assertThat(view.phone()).doesNotContain("1234"); // even the owner's response keeps phone/e-mail masked
	}

	@Test
	void cancelAfterAConcurrentConfirmationIsRefused() {
		doAnswer(inv -> {
			order.setStatus(Order.Status.CONFIRMED);
			return order;
		}).when(locks).lock(order);
		assertThatThrownBy(() -> customerOrders.guestCancel("VOID-001001", tokens.forOrder("VOID-001001")))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("cannot_cancel"));
		verify(orderService, never()).changeStatus(any(), any(), any(), any());
	}

	@Test
	void changingAReturnLocksTheOrderAndTheRequestBeforeReadingTheirState() {
		var em = mock(EntityManager.class);
		var service = new ReturnService(mock(ReturnRepository.class), mock(SettingsService.class), em,
				mock(ApplicationEventPublisher.class), locks);
		var r = mock(ReturnRequest.class);
		when(r.getOrder()).thenReturn(order);
		when(r.getStatus()).thenReturn(ReturnRequest.Status.REQUESTED);

		service.changeStatus(r, ReturnRequest.Status.REJECTED, "no", null, "staff");

		var seq = inOrder(locks, em, r);
		seq.verify(locks).lock(order);
		seq.verify(em).refresh(r, LockModeType.PESSIMISTIC_WRITE);
		seq.verify(r).getStatus();
	}

	@Test
	void creatingAReturnLocksTheOrderFirst() {
		var service = new ReturnService(mock(ReturnRepository.class), mock(SettingsService.class), mock(EntityManager.class),
				mock(ApplicationEventPublisher.class), locks);
		// not delivered → rejected, but only after the lock was taken
		assertThatThrownBy(() -> service.create(order, "EXCHANGE", "SIZE", null, java.util.List.of(), "x")).isInstanceOf(ApiException.class);
		verify(locks).lock(order);
	}
}
