package com.voidstore.api.order;

import com.voidstore.api.auth.AuditLog;
import com.voidstore.api.content.SettingsService;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * PV-4: cancels cash-on-delivery orders that nobody confirmed within {@code unconfirmed_order_hours} (default 72,
 * 0 = off), so abandoned or fake orders cannot keep limited stock reserved. Each order is cancelled in its own
 * transaction under a row lock; staff who confirmed it in the meantime win.
 */
@Component
public class StaleOrderJob {

	private static final Logger log = LoggerFactory.getLogger(StaleOrderJob.class);
	private static final int BATCH = 100;

	private final OrderRepository orders;
	private final OrderService service;
	private final SettingsService settings;
	private final AuditLog audit;

	public StaleOrderJob(OrderRepository orders, OrderService service, SettingsService settings, AuditLog audit) {
		this.orders = orders;
		this.service = service;
		this.settings = settings;
		this.audit = audit;
	}

	@Scheduled(fixedDelay = 15 * 60 * 1000, initialDelay = 2 * 60 * 1000)
	public void run() {
		int hours = settings.integer("unconfirmed_order_hours", 72);
		cancelStale(hours);
	}

	/** @return how many orders were cancelled */
	int cancelStale(int hours) {
		if (hours <= 0) return 0;
		var cutoff = Instant.now().minus(Duration.ofHours(hours));
		int cancelled = 0;
		for (long id : orders.findStaleNewIds(cutoff, PageRequest.of(0, BATCH))) {
			try {
				if (service.autoCancel(id, hours)) cancelled++;
			} catch (RuntimeException e) {
				log.warn("Auto-cancel of order id {} failed: {}", id, e.getMessage());
			}
		}
		if (cancelled > 0) audit.info("order.auto_cancelled", "count", cancelled, "hours", hours);
		return cancelled;
	}
}
