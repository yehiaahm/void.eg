package com.voidstore.api.contact;

import com.voidstore.api.admin.AdminOrderController.PageDto;
import com.voidstore.api.admin.Roles;
import com.voidstore.api.common.ApiException;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin → Messages: what visitors sent from the Contact page. */
@RestController
@RequestMapping("/api/v1/admin/messages")
public class AdminContactController {

	public record MessageDto(long id, String name, String email, String phone, String message, String lang, boolean handled,
			Instant createdAt) {
		static MessageDto of(ContactMessage m) {
			return new MessageDto(m.getId(), m.getName(), m.getEmail(), m.getPhone(), m.getMessage(), m.getLang(), m.isHandled(),
					m.getCreatedAt());
		}
	}

	private final ContactMessageRepository repo;

	public AdminContactController(ContactMessageRepository repo) {
		this.repo = repo;
	}

	@GetMapping
	@PreAuthorize(Roles.STAFF)
	@Transactional(readOnly = true)
	public PageDto<MessageDto> list(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
		Boolean handled = "NEW".equals(status) ? Boolean.FALSE : "HANDLED".equals(status) ? Boolean.TRUE : null;
		String query = q == null || q.isBlank() ? null : q.trim();
		var p = repo.search(handled, query,
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "id")));
		return new PageDto<>(p.map(MessageDto::of).getContent(), p.getTotalElements(), p.getNumber(), p.getSize());
	}

	@GetMapping("/unread")
	@PreAuthorize(Roles.STAFF)
	@Transactional(readOnly = true)
	public long unread() {
		return repo.countByHandledFalse();
	}

	@PutMapping("/{id}/handled")
	@PreAuthorize(Roles.STAFF)
	@Transactional
	public MessageDto setHandled(@PathVariable long id, @RequestParam boolean value) {
		var m = repo.findById(id).orElseThrow(() -> ApiException.notFound("Message"));
		m.setHandled(value);
		return MessageDto.of(m);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize(Roles.ADMIN)
	@Transactional
	public ResponseEntity<Void> delete(@PathVariable long id) {
		repo.delete(repo.findById(id).orElseThrow(() -> ApiException.notFound("Message")));
		return ResponseEntity.noContent().build();
	}
}
