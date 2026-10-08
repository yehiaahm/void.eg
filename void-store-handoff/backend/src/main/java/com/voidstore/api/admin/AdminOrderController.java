package com.voidstore.api.admin;

import static com.voidstore.api.common.Csv.csv;

import com.voidstore.api.auth.CurrentUser;
import com.voidstore.api.common.ApiException;
import com.voidstore.api.order.Order;
import com.voidstore.api.order.OrderDtos;
import com.voidstore.api.order.OrderDtos.EventDto;
import com.voidstore.api.order.OrderDtos.ItemDto;
import com.voidstore.api.order.OrderEvent;
import com.voidstore.api.order.OrderRepository;
import com.voidstore.api.order.OrderService;
import com.voidstore.api.user.User;
import com.voidstore.api.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize(Roles.STAFF)
public class AdminOrderController {

	public static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");

	public record OrderRowDto(long id, String number, String status, String paymentMethod, String paymentStatus,
			Instant createdAt, String name, String phone, String governorate, int itemCount, int total) {}

	public record PageDto<T>(List<T> items, long total, int page, int size) {}

	public record AdminOrderDto(long id, String number, String status, List<String> nextStatuses, String paymentMethod,
			String paymentStatus, String paymentRef, Instant createdAt, Instant updatedAt, String lang, Long customerId,
			String email, String name, String phone, String governorate, String city, String street, String building,
			String addressNotes, String customerNote, List<ItemDto> items, int subtotal, int discount, int shipping,
			int total, String couponCode, List<EventDto> events) {}

	public record StatusReq(@NotBlank String status, @Size(max = 1000) String note) {}

	public record NoteReq(@NotBlank @Size(max = 1000) String note) {}

	public record AddressEditReq(@NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 32) String phone,
			@NotBlank @Size(max = 120) String city, @NotBlank @Size(max = 255) String street,
			@Size(max = 120) String building, @Size(max = 500) String addressNotes) {}

	private final OrderRepository orders;
	private final OrderService service;
	private final UserRepository users;

	public AdminOrderController(OrderRepository orders, OrderService service, UserRepository users) {
		this.orders = orders;
		this.service = service;
		this.users = users;
	}

	static OrderRowDto row(Order o) {
		return new OrderRowDto(o.getId(), o.getNumber(), o.getStatus().name(), o.getPaymentMethod().name(),
				o.getPaymentStatus().name(), o.getCreatedAt(), o.getName(), o.getPhone(), o.getGovernorate(), o.itemCount(),
				o.getTotal());
	}

	static AdminOrderDto dto(Order o) {
		return new AdminOrderDto(o.getId(), o.getNumber(), o.getStatus().name(),
				o.getStatus().next().stream().map(Enum::name).toList(), o.getPaymentMethod().name(),
				o.getPaymentStatus().name(), o.getPaymentRef(), o.getCreatedAt(), o.getUpdatedAt(), o.getLang(),
				o.getUser() == null ? null : o.getUser().getId(), o.getEmail(), o.getName(), o.getPhone(), o.getGovernorate(),
				o.getCity(), o.getStreet(), o.getBuilding(), o.getAddressNotes(), o.getCustomerNote(), OrderDtos.items(o),
				o.getSubtotal(), o.getDiscount(), o.getShipping(), o.getTotal(), o.getCouponCode(),
				o.getEvents().stream().map(e -> new EventDto(e.getType().name(),
						e.getFromStatus() == null ? null : e.getFromStatus().name(),
						e.getToStatus() == null ? null : e.getToStatus().name(), e.getNote(), e.getActorName(), e.getCreatedAt()))
						.toList());
	}

	private static Order.Status parseStatus(String s) {
		if (s == null || s.isBlank() || "ALL".equalsIgnoreCase(s)) return null;
		try {
			return Order.Status.valueOf(s.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw ApiException.badRequest("bad_status", "Unknown status");
		}
	}

	private static Instant day(String d, boolean endExclusive) {
		if (d == null || d.isBlank()) return null;
		var date = LocalDate.parse(d);
		return (endExclusive ? date.plusDays(1) : date).atStartOfDay(CAIRO).toInstant();
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private Order order(String number) {
		return orders.findByNumber(number).orElseThrow(() -> ApiException.notFound("Order"));
	}

	private User actor() {
		return users.findById(CurrentUser.require().id()).orElse(null);
	}

	@GetMapping("/orders")
	@Transactional(readOnly = true)
	public PageDto<OrderRowDto> list(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
			@RequestParam(required = false) String from, @RequestParam(required = false) String to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
		var p = orders.search(parseStatus(status), blankToNull(q), day(from, false), day(to, true),
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "createdAt")));
		return new PageDto<>(p.map(AdminOrderController::row).getContent(), p.getTotalElements(), p.getNumber(), p.getSize());
	}

	@GetMapping("/orders/{number}")
	@Transactional(readOnly = true)
	public AdminOrderDto get(@PathVariable String number) {
		return dto(order(number));
	}

	@PostMapping("/orders/{number}/status")
	@Transactional
	public AdminOrderDto status(@PathVariable String number, @Valid @RequestBody StatusReq req) {
		var to = parseStatus(req.status());
		if (to == null) throw ApiException.badRequest("bad_status", "Unknown status");
		return dto(service.changeStatus(order(number), to, req.note(), actor()));
	}

	@PostMapping("/orders/{number}/notes")
	@Transactional
	public AdminOrderDto note(@PathVariable String number, @Valid @RequestBody NoteReq req) {
		var o = order(number);
		o.getEvents().add(new OrderEvent(o, OrderEvent.Type.NOTE, null, null, req.note().trim(), actor()));
		return dto(o);
	}

	/** Fix a typo in the delivery details (only before the order ships). */
	@PutMapping("/orders/{number}/address")
	@Transactional
	public AdminOrderDto editAddress(@PathVariable String number, @Valid @RequestBody AddressEditReq req) {
		var o = order(number);
		if (o.getStatus() != Order.Status.NEW && o.getStatus() != Order.Status.CONFIRMED) {
			throw ApiException.badRequest("locked", "Delivery details can't change after shipping");
		}
		o.setName(req.name().trim());
		o.setPhone(com.voidstore.api.order.Phones.requireValid(req.phone()));
		o.setCity(req.city().trim());
		o.setStreet(req.street().trim());
		o.setBuilding(req.building());
		o.setAddressNotes(req.addressNotes());
		o.getEvents().add(new OrderEvent(o, OrderEvent.Type.NOTE, null, null, "Delivery details edited", actor()));
		return dto(o);
	}

	@GetMapping(value = "/orders/export.csv", produces = "text/csv")
	@Transactional(readOnly = true)
	public ResponseEntity<byte[]> export(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
			@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
		var list = orders.search(parseStatus(status), blankToNull(q), day(from, false), day(to, true),
				PageRequest.of(0, 10_000, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
		var fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(CAIRO);
		var sb = new StringBuilder("﻿"); // BOM so Excel opens Arabic correctly
		sb.append("Number,Date,Status,Payment,Payment status,Name,Phone,Email,Governorate,City,Street,Items,Subtotal,Discount,Shipping,Total,Coupon\n");
		for (var o : list) {
			String items = o.getItems().stream().map(i -> i.getName() + " " + i.getSize() + " x" + i.getQty())
					.reduce((a, b) -> a + " | " + b).orElse("");
			sb.append(String.join(",", csv(o.getNumber()), csv(fmt.format(o.getCreatedAt())), o.getStatus().name(),
					o.getPaymentMethod().name(), o.getPaymentStatus().name(), csv(o.getName()), csv(o.getPhone()),
					csv(o.getEmail()), csv(o.getGovernorate()), csv(o.getCity()), csv(o.getStreet()), csv(items),
					egp(o.getSubtotal()), egp(o.getDiscount()), egp(o.getShipping()), egp(o.getTotal()),
					csv(o.getCouponCode() == null ? "" : o.getCouponCode()))).append('\n');
		}
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"void-orders-" + LocalDate.now(CAIRO) + ".csv\"")
				.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
				.body(sb.toString().getBytes(StandardCharsets.UTF_8));
	}

	private static String egp(int piastres) {
		return String.format(java.util.Locale.ROOT, "%.2f", piastres / 100.0);
	}
}
