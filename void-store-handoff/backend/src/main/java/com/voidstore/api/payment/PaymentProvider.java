package com.voidstore.api.payment;

import com.voidstore.api.order.Order;

/**
 * A way to pay for an order. Checkout only talks to this interface, so adding Paymob
 * (cards / wallets) means one new {@code @Component} implementing it plus a webhook
 * controller that calls {@link com.voidstore.api.order.OrderService#markPaid}.
 */
public interface PaymentProvider {

	Order.PaymentMethod method();

	boolean isEnabled();

	/** Called right after the order is saved. Return a redirect URL for hosted payment pages, or null. */
	PaymentStart start(Order order);

	record PaymentStart(String redirectUrl) {
		public static final PaymentStart NONE = new PaymentStart(null);
	}
}
