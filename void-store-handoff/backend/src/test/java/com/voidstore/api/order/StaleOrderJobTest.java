package com.voidstore.api.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.voidstore.api.auth.AuditLog;
import com.voidstore.api.auth.ClientIpResolver;
import com.voidstore.api.content.SettingsService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PV-4: unconfirmed cash-on-delivery orders do not keep limited stock reserved forever. */
class StaleOrderJobTest {

	private OrderRepository orders;
	private OrderService service;
	private SettingsService settings;
	private StaleOrderJob job;

	@BeforeEach
	void setUp() {
		orders = mock(OrderRepository.class);
		service = mock(OrderService.class);
		settings = mock(SettingsService.class);
		job = new StaleOrderJob(orders, service, settings, new AuditLog(new ClientIpResolver(ClientIpResolver.DEFAULT_TRUSTED)));
	}

	@Test
	void cancelsEveryStaleOrderAndKeepsGoingAfterAFailure() {
		when(orders.findStaleNewIds(any(Instant.class), any())).thenReturn(List.of(1L, 2L, 3L));
		when(service.autoCancel(1L, 72)).thenReturn(true);
		when(service.autoCancel(2L, 72)).thenThrow(new IllegalStateException("db hiccup"));
		when(service.autoCancel(3L, 72)).thenReturn(true);
		assertThat(job.cancelStale(72)).isEqualTo(2);
		verify(service).autoCancel(3L, 72);
	}

	@Test
	void zeroHoursSwitchesTheJobOff() {
		assertThat(job.cancelStale(0)).isZero();
		verify(orders, never()).findStaleNewIds(any(), any());
		when(settings.integer("unconfirmed_order_hours", 72)).thenReturn(0);
		job.run();
		verify(service, never()).autoCancel(anyLong(), anyInt());
	}

	@Test
	void theSettingDrivesTheCutoff() {
		when(settings.integer("unconfirmed_order_hours", 72)).thenReturn(24);
		when(orders.findStaleNewIds(any(Instant.class), any())).thenReturn(List.of(9L));
		when(service.autoCancel(9L, 24)).thenReturn(true);
		job.run();
		verify(service).autoCancel(9L, 24);
	}
}
