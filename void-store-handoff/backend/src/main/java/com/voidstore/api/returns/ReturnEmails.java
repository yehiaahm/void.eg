package com.voidstore.api.returns;

import static com.voidstore.api.mail.EmailLayout.esc;

import com.voidstore.api.config.AppProperties;
import com.voidstore.api.content.SettingsService;
import com.voidstore.api.mail.EmailLayout;
import com.voidstore.api.mail.MailService;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Emails for return / exchange requests, sent after commit. */
@Component
public class ReturnEmails {

	private static final Map<ReturnRequest.Status, String[]> COPY = Map.of(
			ReturnRequest.Status.REQUESTED, new String[] {"We received your request", "استلمنا طلبك",
					"We'll review it and get back to you shortly.", "هنراجعه ونرد عليك قريب."},
			ReturnRequest.Status.APPROVED, new String[] {"Your request is approved", "تمت الموافقة على طلبك",
					"We'll contact you to arrange picking up the pieces.", "هنتواصل معاك عشان نرتب استلام القطع."},
			ReturnRequest.Status.REJECTED, new String[] {"About your request", "بخصوص طلبك",
					"Unfortunately we can't accept this request. Reply to this email if you have questions.",
					"للأسف مش هنقدر نقبل الطلب ده. رد على الإيميل لو عندك أي سؤال."},
			ReturnRequest.Status.RECEIVED, new String[] {"We received the pieces", "استلمنا القطع",
					"We're processing your request now.", "بنخلّص طلبك دلوقتي."},
			ReturnRequest.Status.COMPLETED, new String[] {"Your request is complete", "تم تنفيذ طلبك",
					"Thanks for shopping VOID.", "شكرًا إنك اخترت VOID."});

	private final ReturnRepository returns;
	private final MailService mail;
	private final SettingsService settings;
	private final AppProperties props;

	public ReturnEmails(ReturnRepository returns, MailService mail, SettingsService settings, AppProperties props) {
		this.returns = returns;
		this.mail = mail;
		this.settings = settings;
		this.props = props;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public void onCreated(ReturnService.Created e) {
		returns.findById(e.requestId()).ifPresent(r -> {
			send(r, ReturnRequest.Status.REQUESTED);
			String staff = settings.get("contact_email", "");
			if (!staff.isBlank()) {
				String kind = r.getType() == ReturnRequest.Type.EXCHANGE ? "Exchange" : "Return";
				mail.send(staff, kind + " request " + r.getNumber() + " · order " + r.getOrder().getNumber(),
						EmailLayout.wrap("en", "<p>" + esc(r.getOrder().getName()) + " asked for a " + kind.toLowerCase()
								+ " (" + esc(r.getReason().name()) + ").</p>"
								+ EmailLayout.button(origin() + "/admin/returns/" + r.getNumber(), "Open in admin")));
			}
		});
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public void onStatus(ReturnService.StatusChanged e) {
		returns.findById(e.requestId()).ifPresent(r -> send(r, e.to()));
	}

	private void send(ReturnRequest r, ReturnRequest.Status status) {
		var c = COPY.get(status);
		if (c == null) return;
		var o = r.getOrder();
		boolean ar = "ar".equals(o.getLang());
		String kind = r.getType() == ReturnRequest.Type.EXCHANGE ? (ar ? "استبدال" : "Exchange") : (ar ? "استرجاع" : "Return");
		String body = "<p>" + esc(ar ? c[3] : c[2]) + "</p><p>" + kind + " <b dir=\"ltr\">" + esc(r.getNumber()) + "</b> · "
				+ (ar ? "طلب " : "order ") + "<span dir=\"ltr\">" + esc(o.getNumber()) + "</span></p>"
				+ EmailLayout.button(origin() + "/" + o.getLang() + "/track?order=" + o.getNumber(), ar ? "تابع الطلب" : "View order");
		mail.send(o.getEmail(), "VOID — " + (ar ? c[1] : c[0]), EmailLayout.wrap(o.getLang(), body));
	}

	private String origin() {
		return props.frontendUrl().split(",")[0].trim();
	}
}
