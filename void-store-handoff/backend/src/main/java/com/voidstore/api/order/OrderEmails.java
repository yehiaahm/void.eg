package com.voidstore.api.order;

import static com.voidstore.api.mail.EmailLayout.esc;

import com.voidstore.api.config.AppProperties;
import com.voidstore.api.content.SettingsService;
import com.voidstore.api.mail.EmailLayout;
import com.voidstore.api.mail.MailService;
import com.voidstore.api.shipping.ShippingZoneRepository;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Order confirmation, status updates and the store's new-order alert — sent only after the DB commit. */
@Component
public class OrderEmails {

	private static final Map<Order.Status, String[]> STATUS_COPY = Map.of(
			Order.Status.CONFIRMED, new String[] {"Your order is confirmed", "تم تأكيد طلبك", "We're preparing your order.", "بنجهّز طلبك دلوقتي."},
			Order.Status.SHIPPED, new String[] {"Your order is on its way", "طلبك في الطريق", "Your order has left our studio and is on its way to you.", "طلبك خرج للشحن وفي الطريق ليك."},
			Order.Status.DELIVERED, new String[] {"Your order was delivered", "تم توصيل طلبك", "Enjoy your pieces. Thank you for shopping VOID.", "استمتع بقطعك. شكرًا إنك اخترت VOID."},
			Order.Status.CANCELLED, new String[] {"Your order was cancelled", "تم إلغاء طلبك", "Your order has been cancelled. If this is unexpected, reply to this email.", "تم إلغاء الطلب. لو ده غير متوقع، رد على الإيميل ده."});

	private final OrderRepository orders;
	private final ShippingZoneRepository zones;
	private final MailService mail;
	private final SettingsService settings;
	private final AppProperties props;

	public OrderEmails(OrderRepository orders, ShippingZoneRepository zones, MailService mail, SettingsService settings,
			AppProperties props) {
		this.orders = orders;
		this.zones = zones;
		this.mail = mail;
		this.settings = settings;
		this.props = props;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public void onPlaced(OrderService.OrderPlaced e) {
		orders.findById(e.orderId()).ifPresent(o -> {
			boolean ar = "ar".equals(o.getLang());
			String body = (ar
					? "<p>أهلاً " + esc(o.getName()) + "،</p><p>استلمنا طلبك رقم <b dir=\"ltr\">" + esc(o.getNumber()) + "</b>. هنتواصل معاك لتأكيده قريب.</p>"
					: "<p>Hi " + esc(o.getName()) + ",</p><p>We received your order <b>" + esc(o.getNumber()) + "</b>. We'll confirm it shortly.</p>")
					+ summary(o, ar) + EmailLayout.button(trackUrl(o), ar ? "تابع طلبك" : "Track your order");
			mail.send(o.getEmail(), (ar ? "VOID — تأكيد استلام الطلب " : "VOID — Order received ") + o.getNumber(),
					EmailLayout.wrap(o.getLang(), body));

			String staff = settings.get("contact_email", "");
			if (!staff.isBlank()) {
				mail.send(staff, "New order " + o.getNumber() + " · " + money(o.getTotal()) + " EGP",
						EmailLayout.wrap("en", "<p>New order from " + esc(o.getName()) + " (" + esc(o.getPhone()) + ").</p>"
								+ summary(o, false)
								+ EmailLayout.button(props.frontendUrl().split(",")[0].trim() + "/admin/orders/" + o.getNumber(), "Open in admin")));
			}
		});
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public void onStatus(OrderService.StatusChanged e) {
		var copy = STATUS_COPY.get(e.to());
		if (copy == null) return;
		orders.findById(e.orderId()).ifPresent(o -> {
			boolean ar = "ar".equals(o.getLang());
			String body = "<p>" + esc(ar ? copy[3] : copy[2]) + "</p><p>" + (ar ? "رقم الطلب: " : "Order: ")
					+ "<b dir=\"ltr\">" + esc(o.getNumber()) + "</b></p>" + EmailLayout.button(trackUrl(o), ar ? "تابع طلبك" : "Track your order");
			mail.send(o.getEmail(), "VOID — " + (ar ? copy[1] : copy[0]), EmailLayout.wrap(o.getLang(), body));
		});
	}

	private String trackUrl(Order o) {
		return props.frontendUrl().split(",")[0].trim() + "/" + o.getLang() + "/track?order=" + o.getNumber();
	}

	private String summary(Order o, boolean ar) {
		var sb = new StringBuilder("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:20px 0;border-top:1px solid rgba(242,240,235,.15)\">");
		for (var i : o.getItems()) {
			sb.append(row(esc(i.getName()) + " · " + esc(i.getSize()) + " × " + i.getQty(), money(i.getLineTotal()), ar));
		}
		sb.append(row(ar ? "المجموع الفرعي" : "Subtotal", money(o.getSubtotal()), ar));
		if (o.getDiscount() > 0) sb.append(row((ar ? "الخصم" : "Discount") + " (" + esc(o.getCouponCode()) + ")", "−" + money(o.getDiscount()), ar));
		sb.append(row(ar ? "الشحن" : "Shipping", money(o.getShipping()), ar));
		sb.append(row("<b>" + (ar ? "الإجمالي" : "Total") + "</b>", "<b>" + money(o.getTotal()) + "</b>", ar));
		sb.append("</table>");
		String zone = zones.findByCode(o.getGovernorate()).map(z -> ar ? z.getNameAr() : z.getNameEn()).orElse(o.getGovernorate());
		sb.append("<p style=\"color:#A7A49D;font-size:13px\">").append(ar ? "التوصيل إلى: " : "Delivering to: ")
				.append(esc(o.getStreet())).append(o.getBuilding().isBlank() ? "" : ", " + esc(o.getBuilding()))
				.append(", ").append(esc(o.getCity())).append(", ").append(esc(zone)).append("<br>")
				.append(ar ? "طريقة الدفع: الدفع عند الاستلام" : "Payment: cash on delivery").append("</p>");
		return sb.toString();
	}

	private static String row(String label, String value, boolean ar) {
		String cur = ar ? " ج.م" : " EGP";
		return "<tr><td style=\"padding:10px 0;border-bottom:1px solid rgba(242,240,235,.1)\">" + label
				+ "</td><td style=\"padding:10px 0;border-bottom:1px solid rgba(242,240,235,.1);text-align:" + (ar ? "left" : "right")
				+ ";white-space:nowrap\" dir=\"ltr\">" + value + cur + "</td></tr>";
	}

	static String money(int piastres) {
		var nf = NumberFormat.getNumberInstance(Locale.US);
		nf.setMaximumFractionDigits(2);
		return nf.format(piastres / 100.0);
	}
}
