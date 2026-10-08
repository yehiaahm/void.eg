package com.voidstore.api.user;

import com.voidstore.api.auth.AuthController.UserDto;
import com.voidstore.api.auth.AuthService;
import com.voidstore.api.auth.CurrentUser;
import com.voidstore.api.common.ApiException;
import com.voidstore.api.order.OrderDtos;
import com.voidstore.api.order.OrderDtos.OrderDto;
import com.voidstore.api.order.OrderDtos.OrderSummaryDto;
import com.voidstore.api.order.OrderRepository;
import com.voidstore.api.order.OrderService;
import com.voidstore.api.returns.CustomerOrders;
import com.voidstore.api.returns.ReturnDtos;
import com.voidstore.api.returns.ReturnService;
import com.voidstore.api.order.Phones;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in customer's own profile, addresses and orders. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

	public record ProfileReq(@NotBlank @Size(max = 120) String name, @Size(max = 32) String phone) {}

	public record PasswordReq(@NotBlank String current, @NotBlank @Size(min = 8, max = 100) String next) {}

	public record AddressReq(
			@NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 32) String phone,
			@NotBlank @Size(max = 40) String governorate, @NotBlank @Size(max = 120) String city,
			@NotBlank @Size(max = 255) String street, @Size(max = 120) String building,
			@Size(max = 500) String notes, Boolean isDefault) {}

	public record AddressDto(long id, String name, String phone, String governorate, String city, String street,
			String building, String notes, boolean isDefault) {
		static AddressDto of(Address a) {
			return new AddressDto(a.getId(), a.getName(), a.getPhone(), a.getGovernorate(), a.getCity(), a.getStreet(),
					a.getBuilding(), a.getNotes(), a.isDefault());
		}
	}

	private final UserRepository users;
	private final AddressRepository addresses;
	private final OrderRepository orders;
	private final OrderService orderService;
	private final AuthService auth;
	private final CustomerOrders customerOrders;
	private final ReturnService returns;

	public MeController(UserRepository users, AddressRepository addresses, OrderRepository orders,
			OrderService orderService, AuthService auth, CustomerOrders customerOrders, ReturnService returns) {
		this.customerOrders = customerOrders;
		this.returns = returns;
		this.users = users;
		this.addresses = addresses;
		this.orders = orders;
		this.orderService = orderService;
		this.auth = auth;
	}

	private User me() {
		return users.findById(CurrentUser.require().id()).orElseThrow(() -> ApiException.notFound("User"));
	}

	@GetMapping
	@Transactional(readOnly = true)
	public UserDto profile() {
		return UserDto.of(me());
	}

	@PatchMapping
	@Transactional
	public UserDto update(@Valid @RequestBody ProfileReq req) {
		var u = me();
		u.setName(req.name().trim());
		u.setPhone(req.phone() == null || req.phone().isBlank() ? null : Phones.requireValid(req.phone()));
		return UserDto.of(u);
	}

	@PostMapping("/password")
	public ResponseEntity<Void> password(@Valid @RequestBody PasswordReq req) {
		auth.changePassword(CurrentUser.require().id(), req.current(), req.next());
		return ResponseEntity.noContent().build();
	}

	// ---- addresses

	@GetMapping("/addresses")
	@Transactional(readOnly = true)
	public List<AddressDto> addresses() {
		return addresses.findAllByUserIdOrderByIsDefaultDescIdAsc(CurrentUser.require().id()).stream().map(AddressDto::of).toList();
	}

	@PostMapping("/addresses")
	@Transactional
	public AddressDto addAddress(@Valid @RequestBody AddressReq req) {
		var u = me();
		var a = new Address(u);
		apply(a, req);
		var existing = addresses.findAllByUserIdOrderByIsDefaultDescIdAsc(u.getId());
		if (existing.isEmpty()) a.setDefault(true);
		if (a.isDefault()) existing.forEach(x -> x.setDefault(false));
		return AddressDto.of(addresses.save(a));
	}

	@PutMapping("/addresses/{id}")
	@Transactional
	public AddressDto updateAddress(@PathVariable long id, @Valid @RequestBody AddressReq req) {
		long uid = CurrentUser.require().id();
		var a = addresses.findByIdAndUserId(id, uid).orElseThrow(() -> ApiException.notFound("Address"));
		apply(a, req);
		if (a.isDefault()) {
			addresses.findAllByUserIdOrderByIsDefaultDescIdAsc(uid).stream().filter(x -> !x.getId().equals(a.getId()))
					.forEach(x -> x.setDefault(false));
		}
		return AddressDto.of(a);
	}

	@DeleteMapping("/addresses/{id}")
	@Transactional
	public ResponseEntity<Void> deleteAddress(@PathVariable long id) {
		var a = addresses.findByIdAndUserId(id, CurrentUser.require().id()).orElseThrow(() -> ApiException.notFound("Address"));
		addresses.delete(a);
		return ResponseEntity.noContent().build();
	}

	private static void apply(Address a, AddressReq req) {
		a.setName(req.name().trim());
		a.setPhone(Phones.requireValid(req.phone()));
		a.setGovernorate(req.governorate());
		a.setCity(req.city().trim());
		a.setStreet(req.street().trim());
		a.setBuilding(req.building());
		a.setNotes(req.notes());
		a.setDefault(Boolean.TRUE.equals(req.isDefault()));
	}

	// ---- orders

	@GetMapping("/orders")
	@Transactional(readOnly = true)
	public List<OrderSummaryDto> myOrders() {
		return orders.findAllByUserIdOrderByCreatedAtDesc(CurrentUser.require().id(), PageRequest.of(0, 100))
				.map(OrderDtos::summary).getContent();
	}

	@GetMapping("/orders/{number}")
	@Transactional(readOnly = true)
	public OrderDto myOrder(@PathVariable String number) {
		long uid = CurrentUser.require().id();
		return orders.findByNumber(number).filter(o -> o.getUser() != null && o.getUser().getId() == uid)
				.map(customerOrders::view).orElseThrow(() -> ApiException.notFound("Order"));
	}

	@PostMapping("/orders/{number}/returns")
	@Transactional
	public OrderDto requestReturn(@PathVariable String number, @Valid @RequestBody ReturnDtos.CreateReq req) {
		long uid = CurrentUser.require().id();
		var o = orders.findByNumber(number).filter(x -> x.getUser() != null && x.getUser().getId() == uid)
				.orElseThrow(() -> ApiException.notFound("Order"));
		returns.create(o, req.type(), req.reason(), req.note(), req.items(), o.getName());
		return customerOrders.view(o);
	}

	@PostMapping("/orders/{number}/cancel")
	@Transactional
	public OrderDto cancel(@PathVariable String number) {
		return customerOrders.view(orderService.cancelByCustomer(number, CurrentUser.require().id()));
	}
}
