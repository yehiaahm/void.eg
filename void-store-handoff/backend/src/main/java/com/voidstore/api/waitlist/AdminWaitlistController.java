package com.voidstore.api.waitlist;

import static com.voidstore.api.common.Csv.csv;

import com.voidstore.api.admin.AdminOrderController;
import com.voidstore.api.admin.AdminOrderController.PageDto;
import com.voidstore.api.admin.Roles;
import com.voidstore.api.common.ApiException;
import com.voidstore.api.common.Csv;
import com.voidstore.api.common.Localized;
import com.voidstore.api.content.SettingsService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Open / close the store, and work through the waitlist collected while it was closed. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminWaitlistController {

	private static final int MAX_MESSAGE = 500;

	public record StoreStatusDto(boolean closed, Localized message, long waiting) {}

	public record StoreStatusReq(boolean closed, Localized message) {}

	public record EntryDto(long id, String name, String phone, String email, String notes, String lang, boolean contacted,
			Instant createdAt) {
		static EntryDto of(WaitlistEntry e) {
			return new EntryDto(e.getId(), e.getName(), e.getPhone(), e.getEmail(), e.getNotes(), e.getLang(), e.isContacted(),
					e.getCreatedAt());
		}
	}

	private final WaitlistRepository repo;
	private final SettingsService settings;

	public AdminWaitlistController(WaitlistRepository repo, SettingsService settings) {
		this.repo = repo;
		this.settings = settings;
	}

	// ================================================================ store open / closed

	@GetMapping("/store-status")
	@PreAuthorize(Roles.STAFF)
	@Transactional(readOnly = true)
	public StoreStatusDto storeStatus() {
		var s = settings.all();
		return new StoreStatusDto(settings.storeClosed(), Localized.of(s.get("closed_message_en"), s.get("closed_message_ar")),
				repo.countByContactedFalse());
	}

	@PutMapping("/store-status")
	@PreAuthorize(Roles.ADMIN)
	@Transactional
	public StoreStatusDto setStoreStatus(@RequestBody StoreStatusReq req) {
		var values = new HashMap<String, String>();
		values.put("store_closed", String.valueOf(req.closed()));
		if (req.message() != null) {
			var m = Localized.of(req.message().en(), req.message().ar());
			if (m.en().length() > MAX_MESSAGE || m.ar().length() > MAX_MESSAGE) {
				throw ApiException.badRequest("message_too_long", "Keep the message under " + MAX_MESSAGE + " characters");
			}
			values.put("closed_message_en", m.en());
			values.put("closed_message_ar", m.ar());
		}
		settings.putAll(values);
		return storeStatus();
	}

	// ================================================================ waitlist

	@GetMapping("/waitlist")
	@PreAuthorize(Roles.STAFF)
	@Transactional(readOnly = true)
	public PageDto<EntryDto> list(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
		var p = repo.search(contacted(status), blankToNull(q),
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "id")));
		return new PageDto<>(p.map(EntryDto::of).getContent(), p.getTotalElements(), p.getNumber(), p.getSize());
	}

	@PutMapping("/waitlist/{id}/contacted")
	@PreAuthorize(Roles.STAFF)
	@Transactional
	public EntryDto setContacted(@PathVariable long id, @RequestParam boolean value) {
		var e = repo.findById(id).orElseThrow(() -> ApiException.notFound("Entry"));
		e.setContacted(value);
		return EntryDto.of(e);
	}

	@DeleteMapping("/waitlist/{id}")
	@PreAuthorize(Roles.ADMIN)
	@Transactional
	public ResponseEntity<Void> delete(@PathVariable long id) {
		repo.delete(repo.findById(id).orElseThrow(() -> ApiException.notFound("Entry")));
		return ResponseEntity.noContent().build();
	}

	@GetMapping(value = "/waitlist/export.csv", produces = "text/csv")
	@PreAuthorize(Roles.STAFF)
	@Transactional(readOnly = true)
	public ResponseEntity<byte[]> export(@RequestParam(required = false) String status, @RequestParam(required = false) String q) {
		var list = repo.search(contacted(status), blankToNull(q), PageRequest.of(0, 10_000, Sort.by(Sort.Direction.DESC, "id")));
		var fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(AdminOrderController.CAIRO);
		var sb = new StringBuilder(Csv.BOM).append("Date,Name,Phone,Email,Notes,Language,Contacted\n");
		for (var e : list) {
			sb.append(String.join(",", csv(fmt.format(e.getCreatedAt())), csv(e.getName()), csv(e.getPhone()),
					csv(e.getEmail()), csv(e.getNotes()), e.getLang(), e.isContacted() ? "yes" : "no")).append('\n');
		}
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"void-waitlist-" + LocalDate.now(AdminOrderController.CAIRO) + ".csv\"")
				.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
				.body(sb.toString().getBytes(StandardCharsets.UTF_8));
	}

	private static Boolean contacted(String status) {
		if ("NEW".equals(status)) return false;
		if ("CONTACTED".equals(status)) return true;
		return null;
	}

	private static String blankToNull(String v) {
		return v == null || v.isBlank() ? null : v.trim();
	}
}
