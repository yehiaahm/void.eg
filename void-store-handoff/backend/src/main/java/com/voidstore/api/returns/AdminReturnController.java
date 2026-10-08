package com.voidstore.api.returns;

import com.voidstore.api.admin.AdminOrderController.PageDto;
import com.voidstore.api.admin.Roles;
import com.voidstore.api.auth.CurrentUser;
import com.voidstore.api.common.ApiException;
import com.voidstore.api.returns.ReturnDtos.EventDto;
import com.voidstore.api.returns.ReturnDtos.ItemDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/returns")
@PreAuthorize(Roles.STAFF)
public class AdminReturnController {

	public record RowDto(String number, String orderNumber, String type, String status, String reason, String customer,
			String phone, int pieces, int value, Instant createdAt) {}

	/** Admin item: adds live stock of the exchange size so staff know if the swap is possible. */
	public record AdminItemDto(ItemDto item, Integer exchangeStock) {}

	public record DetailDto(String number, String orderNumber, String type, String status, List<String> nextStatuses,
			String reason, String note, Integer refundAmount, int itemsValue, Instant createdAt, String customer,
			String phone, String email, List<AdminItemDto> items, List<EventDto> events) {}

	public record StatusReq(@NotBlank String status, @Size(max = 1000) String note, @Min(0) Integer refundAmount) {}

	private final ReturnRepository returns;
	private final ReturnService service;

	public AdminReturnController(ReturnRepository returns, ReturnService service) {
		this.returns = returns;
		this.service = service;
	}

	static RowDto row(ReturnRequest r) {
		var o = r.getOrder();
		return new RowDto(r.getNumber(), o.getNumber(), r.getType().name(), r.getStatus().name(), r.getReason().name(),
				o.getName(), o.getPhone(), r.getItems().stream().mapToInt(ReturnItem::getQty).sum(), r.itemsValue(), r.getCreatedAt());
	}

	static DetailDto detail(ReturnRequest r) {
		var o = r.getOrder();
		var items = r.getItems().stream().map(ri -> {
			Integer stock = null;
			if (ri.getExchangeSize() != null && ri.getOrderItem().getProduct() != null) {
				stock = ri.getOrderItem().getProduct().getVariants().stream()
						.filter(v -> v.getSize().equals(ri.getExchangeSize())).findFirst().map(v -> v.getStock()).orElse(0);
			}
			var oi = ri.getOrderItem();
			return new AdminItemDto(new ItemDto(oi.getId(), oi.getSlug(), oi.getName(), oi.getSize(), oi.getImageUrl(),
					oi.getUnitPrice(), ri.getQty(), ri.getExchangeSize()), stock);
		}).toList();
		return new DetailDto(r.getNumber(), o.getNumber(), r.getType().name(), r.getStatus().name(),
				r.getStatus().next().stream().map(Enum::name).filter(s -> !"CANCELLED".equals(s)).toList(),
				r.getReason().name(), r.getCustomerNote(), r.getRefundAmount(), r.itemsValue(), r.getCreatedAt(),
				o.getName(), o.getPhone(), o.getEmail(), items,
				r.getEvents().stream().map(e -> new EventDto(e.getFromStatus() == null ? null : e.getFromStatus().name(),
						e.getToStatus().name(), e.getNote(), e.getActorName(), e.getCreatedAt())).toList());
	}

	@GetMapping
	@Transactional(readOnly = true)
	public PageDto<RowDto> list(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page) {
		ReturnRequest.Status s = null;
		if (status != null && !status.isBlank() && !"ALL".equals(status)) {
			try {
				s = ReturnRequest.Status.valueOf(status);
			} catch (IllegalArgumentException e) {
				throw ApiException.badRequest("bad_status", "Unknown status");
			}
		}
		var p = returns.search(s, q == null || q.isBlank() ? null : q.trim(),
				PageRequest.of(Math.max(0, page), 25, Sort.by(Sort.Direction.DESC, "createdAt")));
		return new PageDto<>(p.map(AdminReturnController::row).getContent(), p.getTotalElements(), p.getNumber(), p.getSize());
	}

	@GetMapping("/{number}")
	@Transactional(readOnly = true)
	public DetailDto get(@PathVariable String number) {
		return detail(returns.findByNumber(number).orElseThrow(() -> ApiException.notFound("Return")));
	}

	@PostMapping("/{number}/status")
	@Transactional
	public DetailDto status(@PathVariable String number, @Valid @RequestBody StatusReq req) {
		var r = returns.findByNumber(number).orElseThrow(() -> ApiException.notFound("Return"));
		ReturnRequest.Status to;
		try {
			to = ReturnRequest.Status.valueOf(req.status());
		} catch (IllegalArgumentException e) {
			throw ApiException.badRequest("bad_status", "Unknown status");
		}
		if (to == ReturnRequest.Status.CANCELLED) throw ApiException.badRequest("bad_status", "Reject the request instead");
		service.changeStatus(r, to, req.note(), req.refundAmount(), CurrentUser.require().name());
		returns.flush();
		return detail(r);
	}

	/** For the order page in the admin. */
	@GetMapping("/by-order/{orderNumber}")
	@Transactional(readOnly = true)
	public List<RowDto> forOrder(@PathVariable String orderNumber) {
		return returns.search(null, orderNumber, PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt")))
				.map(AdminReturnController::row).getContent().stream().filter(r -> r.orderNumber().equals(orderNumber)).toList();
	}
}
