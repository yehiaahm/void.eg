package com.voidstore.api.order;

import com.voidstore.api.config.AppProperties;
import com.voidstore.api.whatsapp.WhatsAppService;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * WhatsApp order updates, mirroring {@link OrderEmails}: sent after the DB commit, only to customers who ticked the
 * opt-in box at checkout (WhatsApp requires it), and only while the owner has WhatsApp switched on and configured.
 */
@Component
public class OrderWhatsApp {

	private final OrderRepository orders;
	private final WhatsAppService whatsapp;
	private final AppProperties props;
	private final TrackingTokens tokens;

	public OrderWhatsApp(OrderRepository orders, WhatsAppService whatsapp, AppProperties props, TrackingTokens tokens) {
		this.orders = orders;
		this.whatsapp = whatsapp;
		this.props = props;
		this.tokens = tokens;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public void onPlaced(OrderService.OrderPlaced e) {
		send(e.orderId(), "placed");
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public void onStatus(OrderService.StatusChanged e) {
		String step = switch (e.to()) {
			case CONFIRMED -> "confirmed";
			case SHIPPED -> "shipped";
			case DELIVERED -> "delivered";
			case CANCELLED -> "cancelled";
			default -> null;
		};
		if (step != null) send(e.orderId(), step);
	}

	private void send(long orderId, String step) {
		if (!whatsapp.isActive()) return;
		orders.findById(orderId).filter(Order::isWhatsappOptIn).ifPresent(o -> {
			String to = Phones.international(o.getPhone());
			if (to == null) return;
			// template variables: {{1}} first name, {{2}} order number, {{3}} private tracking link
			whatsapp.sendTemplate(to, whatsapp.templateName(step), "ar".equals(o.getLang()) ? "ar" : "en",
					List.of(firstName(o.getName()), o.getNumber(), trackUrl(o)));
		});
	}

	static String firstName(String full) {
		String n = full == null ? "" : full.trim();
		int sp = n.indexOf(' ');
		return sp > 0 ? n.substring(0, sp) : n;
	}

	private String trackUrl(Order o) {
		return props.frontendUrl().split(",")[0].trim() + "/" + o.getLang() + "/track?order=" + o.getNumber()
				+ "&t=" + tokens.forOrder(o.getNumber());
	}
}
