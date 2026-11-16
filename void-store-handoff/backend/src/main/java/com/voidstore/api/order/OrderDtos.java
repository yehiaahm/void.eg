package com.voidstore.api.order;

import com.voidstore.api.common.Localized;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {
	private OrderDtos() {}

	// ---- requests ----

	public record LineReq(@NotBlank @Size(max = 120) String slug, @NotBlank @Size(max = 8) String size, @Min(1) @Max(10) int qty) {}

	public record QuoteReq(@NotEmpty @Size(max = 30) List<@Valid LineReq> items, @Size(max = 40) String governorate,
			@Size(max = 40) String couponCode, @Size(max = 190) String email, @Size(max = 32) String phone) {}

	public record ContactReq(
			@NotBlank @Email @Size(max = 190) String email,
			@NotBlank @Size(max = 120) String name,
			@NotBlank @Size(max = 32) String phone) {}

	public record AddressReq(
			@NotBlank @Size(max = 40) String governorate,
			@NotBlank @Size(max = 120) String city,
			@NotBlank @Size(max = 255) String street,
			@Size(max = 120) String building,
			@Size(max = 500) String notes) {}

	public record PlaceReq(
			@NotEmpty @Size(max = 30) List<@Valid LineReq> items,
			@NotNull @Valid ContactReq contact,
			@NotNull @Valid AddressReq address,
			@Size(max = 1000) String note,
			@Size(max = 40) String couponCode,
			@NotBlank @Size(max = 16) String paymentMethod,
			@Size(max = 5) String lang,
			Boolean saveAddress,
			Boolean whatsappOptIn) {}

	/**
	 * Guest proof of ownership. {@code token} (from the e-mailed link) gives the full order and the right to cancel or
	 * return; {@code phone} alone only gives a masked, read-only status view.
	 */
	public record TrackReq(@NotBlank @Size(max = 20) String number, @Size(max = 32) String phone, @Size(max = 128) String token) {}

	/** One order a device remembers: the number and its private tracking token (never the phone). */
	public record BatchEntry(@NotBlank @Size(max = 20) String number, @NotBlank @Size(max = 128) String token) {}

	/** The orders a device remembers, checked in one call (home strip, "my orders"). */
	public record TrackBatchReq(@NotEmpty @Size(max = 10) List<@Valid BatchEntry> orders) {}

	// ---- responses ----

	/** issue: null when fine, else unavailable | no_price | out_of_stock | insufficient_stock */
	public record QuoteLine(String slug, String size, int qty, Localized name, Integer unitPrice, int lineTotal,
			int available, String image, String issue) {}

	public record QuoteRes(List<QuoteLine> items, int subtotal, int discount, Integer shipping, Integer total,
			String couponCode, String couponError, boolean freeShipping, List<String> paymentMethods) {}

	/** {@code trackingToken}: the guest's private link secret, shown once here and in the confirmation e-mail. */
	public record PlaceRes(String number, int total, String paymentMethod, String redirectUrl, String trackingToken) {}

	/** returnableQty: pieces the customer can still ask to return / exchange (0 when not eligible). */
	public record ItemDto(long id, String slug, String name, String size, String sku, String image, int unitPrice, int qty,
			int lineTotal, int returnedQty, int returnableQty) {}

	public record EventDto(String type, String fromStatus, String toStatus, String note, String actor, Instant at) {}

	/** Customer-facing order (tracking page, account). */
	public record OrderDto(String number, String status, String paymentMethod, String paymentStatus,
			Instant createdAt, String name, String phone, String email, String governorate, String city, String street,
			String building, String addressNotes, List<ItemDto> items, int subtotal, int discount, int shipping, int total,
			String couponCode, List<EventDto> timeline, boolean canCancel, Instant returnUntil,
			List<com.voidstore.api.returns.ReturnDtos.ReturnDto> returns) {}

	/**
	 * @param returnable  order item id → pieces still returnable (empty map = none)
	 * @param returnUntil deadline for return / exchange requests, null when not eligible
	 */
	public static OrderDto of(Order o, java.util.Map<Long, Integer> returnable, Instant returnUntil, boolean canCancel,
			List<com.voidstore.api.returns.ReturnDtos.ReturnDto> returns) {
		var items = o.getItems().stream().map(i -> new ItemDto(i.getId(), i.getSlug(), i.getName(), i.getSize(), i.getSku(),
				i.getImageUrl(), i.getUnitPrice(), i.getQty(), i.getLineTotal(), i.getReturnedQty(),
				returnUntil == null ? 0 : returnable.getOrDefault(i.getId(), 0))).toList();
		return new OrderDto(o.getNumber(), o.getStatus().name(), o.getPaymentMethod().name(), o.getPaymentStatus().name(),
				o.getCreatedAt(), o.getName(), o.getPhone(), o.getEmail(), o.getGovernorate(), o.getCity(), o.getStreet(),
				o.getBuilding(), o.getAddressNotes(), items, o.getSubtotal(), o.getDiscount(), o.getShipping(),
				o.getTotal(), o.getCouponCode(),
				o.getEvents().stream()
						.filter(e -> e.getType() == OrderEvent.Type.PLACED || e.getType() == OrderEvent.Type.STATUS)
						.map(e -> new EventDto(e.getType().name(), name(e.getFromStatus()), name(e.getToStatus()), "", "", e.getCreatedAt()))
						.toList(),
				canCancel, returnUntil, returns);
	}

	/**
	 * Reduces a customer view for guests so personal data is not exposed unnecessarily (VOID-04). Phone and e-mail
	 * are always masked. With {@code full} (the caller holds the private token) name and delivery address stay
	 * visible; without it (phone-only proof) the view is read-only status information with no address, no actions.
	 */
	public static OrderDto forGuest(OrderDto d, boolean full) {
		var returns = d.returns().stream().map(r -> new com.voidstore.api.returns.ReturnDtos.ReturnDto(r.number(), r.type(),
				r.status(), r.reason(), "", r.refundAmount(), r.createdAt(), r.items(), r.timeline())).toList();
		if (full) {
			return new OrderDto(d.number(), d.status(), d.paymentMethod(), d.paymentStatus(), d.createdAt(), d.name(),
					maskPhone(d.phone()), maskEmail(d.email()), d.governorate(), d.city(), d.street(), d.building(),
					d.addressNotes(), d.items(), d.subtotal(), d.discount(), d.shipping(), d.total(), d.couponCode(),
					d.timeline(), d.canCancel(), d.returnUntil(), d.returns());
		}
		var items = d.items().stream().map(i -> new ItemDto(i.id(), i.slug(), i.name(), i.size(), i.sku(), i.image(),
				i.unitPrice(), i.qty(), i.lineTotal(), i.returnedQty(), 0)).toList();
		return new OrderDto(d.number(), d.status(), d.paymentMethod(), d.paymentStatus(), d.createdAt(), initials(d.name()),
				maskPhone(d.phone()), maskEmail(d.email()), d.governorate(), "", "", "", "", items, d.subtotal(),
				d.discount(), d.shipping(), d.total(), null, d.timeline(), false, null, returns);
	}

	static String maskPhone(String p) {
		if (p == null || p.length() < 6) return "";
		return p.substring(0, 3) + "*".repeat(p.length() - 5) + p.substring(p.length() - 2);
	}

	static String maskEmail(String e) {
		if (e == null) return "";
		int at = e.indexOf('@');
		if (at < 1) return "";
		return e.charAt(0) + "***" + e.substring(at);
	}

	static String initials(String name) {
		if (name == null || name.isBlank()) return "";
		var sb = new StringBuilder();
		for (String part : name.trim().split("\s+")) sb.append(part.codePointAt(0) == 0 ? "" : new String(Character.toChars(part.codePointAt(0)))).append(". ");
		return sb.toString().trim();
	}

	public static List<ItemDto> items(Order o) {
		return o.getItems().stream().map(i -> new ItemDto(i.getId(), i.getSlug(), i.getName(), i.getSize(), i.getSku(),
				i.getImageUrl(), i.getUnitPrice(), i.getQty(), i.getLineTotal(), i.getReturnedQty(), 0)).toList();
	}

	public record OrderSummaryDto(String number, String status, Instant createdAt, int itemCount, int total) {}

	public static OrderSummaryDto summary(Order o) {
		return new OrderSummaryDto(o.getNumber(), o.getStatus().name(), o.getCreatedAt(), o.itemCount(), o.getTotal());
	}

	static String name(Enum<?> e) {
		return e == null ? null : e.name();
	}
}
