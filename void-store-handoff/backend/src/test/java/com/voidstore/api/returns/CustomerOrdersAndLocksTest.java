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
import com.voidstore.api.order.OrderLocks;
import com.voidstore.api.order.OrderRepository;
import com.voidstore.api.order.OrderService;
import com.voidstore.api.order.TrackingTokens;
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
