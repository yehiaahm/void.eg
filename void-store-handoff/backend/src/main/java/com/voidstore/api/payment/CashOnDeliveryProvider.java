package com.voidstore.api.payment;

import com.voidstore.api.content.SettingsService;
import com.voidstore.api.order.Order;
import org.springframework.stereotype.Component;

/** Cash on delivery: nothing to do at checkout; marked paid when the order is delivered. */
@Component
public class CashOnDeliveryProvider implements PaymentProvider {

	private final SettingsService settings;

	public CashOnDeliveryProvider(SettingsService settings) {
		this.settings = settings;
	}

	@Override
	public Order.PaymentMethod method() {
		return Order.PaymentMethod.COD;
	}

	@Override
	public boolean isEnabled() {
		return settings.bool("cod_enabled", true);
	}

	@Override
	public PaymentStart start(Order order) {
		return PaymentStart.NONE;
	}
}
