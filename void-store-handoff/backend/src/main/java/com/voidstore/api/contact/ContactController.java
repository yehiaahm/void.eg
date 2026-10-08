package com.voidstore.api.contact;

import static com.voidstore.api.mail.EmailLayout.esc;

import com.voidstore.api.auth.RateLimiter;
import com.voidstore.api.config.AppProperties;
import com.voidstore.api.content.SettingsService;
import com.voidstore.api.mail.EmailLayout;
import com.voidstore.api.mail.MailService;
import com.voidstore.api.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The Contact page form: name, email and message (phone optional). Stored for Admin → Messages and emailed to the store. */
@RestController
@RequestMapping("/api/v1/contact")
public class ContactController {

	public record ContactReq(@NotBlank @Size(max = 120) String name, @NotBlank @Email @Size(max = 190) String email,
			@Size(max = 32) @Pattern(regexp = "^[+0-9\\u0660-\\u0669\\u06F0-\\u06F9 ()\\-]*$") String phone,
			@NotBlank @Size(max = 2000) String message, String lang) {}

	private final ContactMessageRepository repo;
	private final RateLimiter limiter;
	private final MailService mail;
	private final SettingsService settings;
	private final AppProperties props;

	public ContactController(ContactMessageRepository repo, RateLimiter limiter, MailService mail, SettingsService settings,
			AppProperties props) {
		this.repo = repo;
		this.limiter = limiter;
		this.mail = mail;
		this.settings = settings;
		this.props = props;
	}

	@PostMapping
	public ResponseEntity<Void> send(@Valid @RequestBody ContactReq req, HttpServletRequest http) {
		limiter.check("contact:" + http.getRemoteAddr(), 5, Duration.ofMinutes(10));
		var m = new ContactMessage();
		m.setName(req.name().trim());
		m.setEmail(User.normalizeEmail(req.email()));
		if (req.phone() != null && !req.phone().isBlank()) m.setPhone(req.phone().trim());
		m.setMessage(req.message().trim());
		m.setLang("ar".equals(req.lang()) ? "ar" : "en");
		repo.save(m);

		String staff = settings.get("contact_email", "");
		if (!staff.isBlank()) {
			String body = "<p>New message from <b>" + esc(m.getName()) + "</b> &lt;<a href=\"mailto:" + esc(m.getEmail())
					+ "\" style=\"color:#F2F0EB\">" + esc(m.getEmail()) + "</a>&gt;"
					+ (m.getPhone() == null ? "" : " · <span dir=\"ltr\">" + esc(m.getPhone()) + "</span>") + "</p>"
					+ "<p style=\"white-space:pre-line;color:#A7A49D\">" + esc(m.getMessage()) + "</p>"
					+ EmailLayout.button(props.frontendUrl().split(",")[0].trim() + "/admin/messages", "Open in admin");
			mail.send(staff, "Contact form · " + m.getName(), EmailLayout.wrap("en", body));
		}
		return ResponseEntity.noContent().build();
	}
}
