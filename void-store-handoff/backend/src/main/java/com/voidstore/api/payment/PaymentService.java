package com.voidstore.api.payment;

import com.voidstore.api.common.ApiException;
import com.voidstore.api.order.Order;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

	private final Map<Order.PaymentMethod, PaymentProvider> providers = new EnumMap<>(Order.PaymentMethod.class);

	public PaymentService(List<PaymentProvider> list) {
		list.forEach(p -> providers.put(p.method(), p));
	}

	public PaymentProvider require(Order.PaymentMethod method) {
		var p = providers.get(method);
		if (p == null || !p.isEnabled()) {
			throw ApiException.badRequest("payment_unavailable", "This payment method is not available");
		}
		return p;
	}

	public List<String> enabledMethods() {
		return providers.values().stream().filter(PaymentProvider::isEnabled).map(p -> p.method().name()).toList();
	}
}
