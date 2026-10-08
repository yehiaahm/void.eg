package com.voidstore.api.admin;

import com.voidstore.api.auth.CurrentUser;
import com.voidstore.api.auth.RefreshTokenRepository;
import com.voidstore.api.common.ApiException;
import com.voidstore.api.common.Localized;
import com.voidstore.api.content.PageRepository;
import com.voidstore.api.content.SettingsService;
import com.voidstore.api.order.Coupon;
import com.voidstore.api.order.CouponRepository;
import com.voidstore.api.order.OrderDtos;
import com.voidstore.api.order.OrderRepository;
import com.voidstore.api.shipping.ShippingZoneRepository;
import com.voidstore.api.user.AddressRepository;
import com.voidstore.api.user.User;
import com.voidstore.api.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
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
public class AdminStoreController {

	private final UserRepository users;
	private final AddressRepository addresses;
	private final OrderRepository orders;
	private final CouponRepository coupons;
	private final ShippingZoneRepository zones;
	private final SettingsService settings;
	private final PageRepository pages;
	private final PasswordEncoder encoder;
	private final RefreshTokenRepository refreshTokens;

	public AdminStoreController(UserRepository users, AddressRepository addresses, OrderRepository orders,
			CouponRepository coupons, ShippingZoneRepository zones, SettingsService settings, PageRepository pages,
			PasswordEncoder encoder, RefreshTokenRepository refreshTokens) {
		this.users = users;
		this.addresses = addresses;
		this.orders = orders;
		this.coupons = coupons;
		this.zones = zones;
		this.settings = settings;
		this.pages = pages;
		this.encoder = encoder;
		this.refreshTokens = refreshTokens;
	}

	// ================================================================ customers

	public record CustomerRowDto(long id, String name, String email, String phone, Instant createdAt, long orders, long spent) {}

	public record CustomerDto(long id, String name, String email, String phone, Instant createdAt, Instant lastLoginAt,
			boolean enabled, long orderCount, long spent, List<OrderDtos.OrderSummaryDto> orders,
			List<Map<String, Object>> addresses) {}

	@GetMapping("/customers")
	@PreAuthorize(Roles.STAFF)
	@Transactional(readOnly = true)
	public AdminOrderController.PageDto<CustomerRowDto> customers(@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
		var p = users.searchCustomers(q == null || q.isBlank() ? null : q.trim(),
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "createdAt")));
		var rows = p.map(u -> new CustomerRowDto(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getCreatedAt(),
				orders.countByUserId(u.getId()), orders.totalSpentByUser(u.getId()))).getContent();
		return new AdminOrderController.PageDto<>(rows, p.getTotalElements(), p.getNumber(), p.getSize());
	}

	@GetMapping("/customers/{id}")
	@PreAuthorize(Roles.STAFF)
	@Transactional(readOnly = true)
	public CustomerDto customer(@PathVariable long id) {
		var u = users.findById(id).filter(x -> x.getRole() == User.Role.CUSTOMER)
				.orElseThrow(() -> ApiException.notFound("Customer"));
		var list = orders.findAllByUserIdOrderByCreatedAtDesc(id, PageRequest.of(0, 100)).map(OrderDtos::summary).getContent();
		var addr = addresses.findAllByUserIdOrderByIsDefaultDescIdAsc(id).stream().<Map<String, Object>>map(a -> Map.of(
				"name", a.getName(), "phone", a.getPhone(), "governorate", a.getGovernorate(), "city", a.getCity(),
				"street", a.getStreet(), "building", a.getBuilding(), "isDefault", a.isDefault())).toList();
		return new CustomerDto(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getCreatedAt(), u.getLastLoginAt(),
				u.isEnabled(), orders.countByUserId(id), orders.totalSpentByUser(id), list, addr);
	}

	@PutMapping("/customers/{id}/enabled")
	@PreAuthorize(Roles.ADMIN)
	@Transactional
	public ResponseEntity<Void> setCustomerEnabled(@PathVariable long id, @RequestParam boolean value) {
		var u = users.findById(id).filter(x -> x.getRole() == User.Role.CUSTOMER)
				.orElseThrow(() -> ApiException.notFound("Customer"));
		u.setEnabled(value);
		if (!value) refreshTokens.revokeAllForUser(id);
		return ResponseEntity.noContent().build();
	}

	// ================================================================ coupons

	public record CouponDto(long id, String code, String type, int value, int minSubtotal, Instant startsAt, Instant endsAt,
			Integer maxUses, Integer perCustomer, int usedCount, boolean active, Instant createdAt) {
		static CouponDto of(Coupon c) {
			return new CouponDto(c.getId(), c.getCode(), c.getType().name(), c.getValue(), c.getMinSubtotal(), c.getStartsAt(),
					c.getEndsAt(), c.getMaxUses(), c.getPerCustomer(), c.getUsedCount(), c.isActive(), c.getCreatedAt());
		}
	}

	public record CouponReq(
			@NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "letters, numbers, - and _ only") String code,
			@NotBlank String type, @Min(0) int value, @Min(0) int minSubtotal, Instant startsAt, Instant endsAt,
			@Min(1) Integer maxUses, @Min(1) Integer perCustomer, boolean active) {}

	@GetMapping("/coupons")
	@PreAuthorize(Roles.ADMIN)
	@Transactional(readOnly = true)
	public List<CouponDto> listCoupons() {
		return coupons.findAllByOrderByCreatedAtDesc().stream().map(CouponDto::of).toList();
	}

	@PostMapping("/coupons")
	@PreAuthorize(Roles.ADMIN)
	@Transactional
	public CouponDto createCoupon(@Valid @RequestBody CouponReq req) {
		if (coupons.existsByCode(Coupon.normalize(req.code()))) throw ApiException.conflict("code_taken", "This code already exists");
		var c = new Coupon();
		applyCoupon(c, req);
		return CouponDto.of(coupons.save(c));
	}

	@PutMapping("/coupons/{id}")
	@PreAuthorize(Roles.ADMIN)
	@Transactional
	public CouponDto updateCoupon(@PathVariable long id, @Valid @RequestBody CouponReq req) {
		var c = coupons.findById(id).orElseThrow(() -> ApiException.notFound("Coupon"));
		if (!c.getCode().equals(Coupon.normalize(req.code())) && coupons.existsByCode(Coupon.normalize(req.code()))) {
			throw ApiException.conflict("code_taken", "This code already exists");
		}
		applyCoupon(c, req);
		return CouponDto.of(c);
	}

	@DeleteMapping("/coupons/{id}")
	@PreAuthorize(Roles.ADMIN)
	@Transactional
	public ResponseEntity<Void> deleteCoupon(@PathVariable long id) {
		var c = coupons.findById(id).orElseThrow(() -> ApiException.notFound("Coupon"));
		if (c.getUsedCount() > 0) c.setActive(false); // keep history of used codes
		else coupons.delete(c);
		return ResponseEntity.noContent().build();
	}

	private static void applyCoupon(Coupon c, CouponReq r) {
		Coupon.Type type;
		try {
			type = Coupon.Type.valueOf(r.type());
		} catch (IllegalArgumentException e) {
			throw ApiException.badRequest("bad_type", "Unknown coupon type");
		}
		if (type == Coupon.Type.PERCENT && (r.value() < 1 || r.value() > 100)) {
			throw ApiException.badRequest("bad_value", "Percent must be between 1 and 100");
		}
		if (type == Coupon.Type.FIXED && r.value() < 1) throw ApiException.badRequest("bad_value", "Amount must be positive");
		if (r.startsAt() != null && r.endsAt() != null && r.endsAt().isBefore(r.startsAt())) {
			throw ApiException.badRequest("bad_dates", "End date is before start date");
		}
		c.setCode(r.code());
		c.setType(type);
		c.setValue(type == Coupon.Type.FREE_SHIPPING ? 0 : r.value());
		c.setMinSubtotal(r.minSubtotal());
		c.setStartsAt(r.startsAt());
		c.setEndsAt(r.endsAt());
		c.setMaxUses(r.maxUses());
		c.setPerCustomer(r.perCustomer());
		c.setActive(r.active());
	}

	// ================================================================ shipping

	public record ZoneDto(long id, String code, Localized name, Integer fee, String etaDays, boolean enabled) {}

	public record ZoneReq(@NotNull Long id, @Min(0) @Max(10_000_00) Integer fee, @Size(max = 16) String etaDays, boolean enabled) {}

	@GetMapping("/shipping-zones")
	@PreAuthorize(Roles.STAFF)
	@Transactional(readOnly = true)
	public List<ZoneDto> zones() {
		return zones.findAllByOrderBySortOrderAsc().stream().map(z -> new ZoneDto(z.getId(), z.getCode(),
				Localized.of(z.getNameEn(), z.getNameAr()), z.getFee(), z.getEtaDays(), z.isEnabled())).toList();
	}

	@PutMapping("/shipping-zones")
	@PreAuthorize(Roles.ADMIN)
	@Transactional
	public List<ZoneDto> saveZones(@RequestBody List<@Valid ZoneReq> reqs) {
		for (var r : reqs) {
			var z = zones.findById(r.id()).orElseThrow(() -> ApiException.notFound("Zone"));
			if (r.enabled() && r.fee() == null) {
				throw ApiException.badRequest("fee_required", "Set a fee for " + z.getNameEn() + " before enabling it");
			}
			z.setFee(r.fee());
			z.setEtaDays(r.etaDays());
			z.setEnabled(r.enabled());
		}
		return zones();
	}

	// ================================================================ settings & pages

	@GetMapping("/settings")
	@PreAuthorize(Roles.ADMIN)
	public Map<String, String> getSettings() {
		return settings.all();
	}

	@PutMapping("/settings")
	@PreAuthorize(Roles.ADMIN)
	public Map<String, String> saveSettings(@RequestBody Map<String, String> values) {
		values.forEach((k, v) -> {
			if ((k.endsWith("_url")) && v != null && !v.isBlank() && !v.matches("https://\\S+")) {
				throw ApiException.badRequest("bad_url", "Links must start with https://");
			}
		});
		settings.putAll(values);
		return settings.all();
	}

	public record PageDto(String slug, Localized title, Localized body, Instant updatedAt) {}

	public record PageReq(@NotNull Localized title, @NotNull Localized body) {}

	@GetMapping("/pages")
	@PreAuthorize(Roles.ADMIN)
	@Transactional(readOnly = true)
	public List<PageDto> listPages() {
		return pages.findAllByOrderByIdAsc().stream().map(p -> new PageDto(p.getSlug(), Localized.of(p.getTitleEn(), p.getTitleAr()),
				Localized.of(p.getBodyEn(), p.getBodyAr()), p.getUpdatedAt())).toList();
	}

	@PutMapping("/pages/{slug}")
	@PreAuthorize(Roles.ADMIN)
	@Transactional
	public PageDto savePage(@PathVariable String slug, @Valid @RequestBody PageReq req) {
		var p = pages.findBySlug(slug).orElseThrow(() -> ApiException.notFound("Page"));
		p.setTitleEn(req.title().en());
		p.setTitleAr(req.title().ar());
		p.setBodyEn(req.body().en() == null ? "" : req.body().en());
		p.setBodyAr(req.body().ar() == null ? "" : req.body().ar());
		return new PageDto(p.getSlug(), req.title(), req.body(), Instant.now());
	}

	// ================================================================ staff (owner only)

	private static final Set<User.Role> STAFF_ROLES = Set.of(User.Role.STAFF, User.Role.ADMIN, User.Role.OWNER);

	public record StaffDto(long id, String name, String email, String role, boolean enabled, Instant lastLoginAt, Instant createdAt) {
		static StaffDto of(User u) {
			return new StaffDto(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.isEnabled(), u.getLastLoginAt(), u.getCreatedAt());
		}
	}

	public record StaffCreateReq(@NotBlank @Size(max = 120) String name, @NotBlank @Email String email,
			@NotBlank String role, @NotBlank @Size(min = 10, max = 100) String password) {}

	public record StaffUpdateReq(@NotBlank String role, boolean enabled, @Size(min = 10, max = 100) String password) {}

	@GetMapping("/staff")
	@PreAuthorize(Roles.OWNER)
	@Transactional(readOnly = true)
	public List<StaffDto> staff() {
		return users.findAllByRoleInOrderByCreatedAtAsc(STAFF_ROLES).stream().map(StaffDto::of).toList();
	}

	@PostMapping("/staff")
	@PreAuthorize(Roles.OWNER)
	@Transactional
	public StaffDto createStaff(@Valid @RequestBody StaffCreateReq req) {
		var role = staffRole(req.role());
		var email = User.normalizeEmail(req.email());
		var existing = users.findByEmail(email).orElse(null);
		if (existing != null && existing.getRole() != User.Role.CUSTOMER) {
			throw ApiException.conflict("email_taken", "This person already has staff access");
		}
		var u = existing != null ? existing : new User(email, "", req.name().trim(), role);
		u.setName(req.name().trim());
		u.setRole(role);
		u.setEnabled(true);
		u.setPasswordHash(encoder.encode(req.password()));
		return StaffDto.of(users.save(u));
	}

	@PutMapping("/staff/{id}")
	@PreAuthorize(Roles.OWNER)
	@Transactional
	public StaffDto updateStaff(@PathVariable long id, @Valid @RequestBody StaffUpdateReq req) {
		var u = users.findById(id).filter(x -> STAFF_ROLES.contains(x.getRole()))
				.orElseThrow(() -> ApiException.notFound("Staff member"));
		if (u.getId() == CurrentUser.require().id()) {
			throw ApiException.badRequest("self", "You can't change your own role or access");
		}
		if ("CUSTOMER".equals(req.role())) {
			u.setRole(User.Role.CUSTOMER); // remove staff access, keep the person's shopping account
		} else {
			u.setRole(staffRole(req.role()));
		}
		u.setEnabled(req.enabled());
		if (req.password() != null && !req.password().isBlank()) u.setPasswordHash(encoder.encode(req.password()));
		refreshTokens.revokeAllForUser(u.getId()); // role changes take effect on next sign-in
		return StaffDto.of(u);
	}

	private static User.Role staffRole(String s) {
		try {
			var r = User.Role.valueOf(s);
			if (r == User.Role.STAFF || r == User.Role.ADMIN) return r;
		} catch (IllegalArgumentException ignored) {
			// fall through
		}
		throw ApiException.badRequest("bad_role", "Role must be STAFF or ADMIN");
	}
}
