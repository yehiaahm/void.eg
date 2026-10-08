package com.voidstore.api.waitlist;

import com.voidstore.api.auth.RateLimiter;
import com.voidstore.api.order.Phones;
import com.voidstore.api.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The form shoppers see while the store is closed: name + mobile, optional email and notes. */
@RestController
@RequestMapping("/api/v1/waitlist")
public class WaitlistController {

	public record JoinReq(@NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 32) String phone,
			@Email @Size(max = 190) String email, @Size(max = 1000) String notes, String lang) {}

	private final WaitlistRepository repo;
	private final RateLimiter limiter;

	public WaitlistController(WaitlistRepository repo, RateLimiter limiter) {
		this.repo = repo;
		this.limiter = limiter;
	}

	@PostMapping
	@Transactional
	public ResponseEntity<Void> join(@Valid @RequestBody JoinReq req, HttpServletRequest http) {
		limiter.check("waitlist:" + http.getRemoteAddr(), 5, Duration.ofMinutes(10));
		String phone = Phones.requireValid(req.phone());
		// Signing up again with the same number updates the entry the owner hasn't handled yet.
		var e = repo.findFirstByPhoneAndContactedFalseOrderByIdDesc(phone).orElseGet(WaitlistEntry::new);
		e.setName(req.name().trim());
		e.setPhone(phone);
		if (req.email() != null && !req.email().isBlank()) e.setEmail(User.normalizeEmail(req.email()));
		if (req.notes() != null && !req.notes().isBlank()) e.setNotes(req.notes().trim());
		e.setLang("ar".equals(req.lang()) ? "ar" : "en");
		repo.save(e);
		return ResponseEntity.noContent().build();
	}
}
