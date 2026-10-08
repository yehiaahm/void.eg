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

	public record LineReq(@NotBlank String slug, @NotBlank String size, @Min(1) @Max(10) int qty) {}

	public record QuoteReq(@NotEmpty @Size(max = 30) List<@Valid LineReq> items, String governorate, String couponCode, String email) {}

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
			String couponCode,
			@NotBlank String paymentMethod,
			String lang,
			Boolean saveAddress) {}

	public record TrackReq(@NotBlank @Size(max = 20) String number, @NotBlank @Size(max = 32) String phone) {}

	// ---- responses ----

	/** issue: null when fine, else unavailable | no_price | out_of_stock | insufficient_stock */
	public record QuoteLine(String slug, String size, int qty, Localized name, Integer unitPrice, int lineTotal,
			int available, String image, String issue) {}

	public record QuoteRes(List<QuoteLine> items, int subtotal, int discount, Integer shipping, Integer total,
			String couponCode, String couponError, boolean freeShipping, List<String> paymentMethods) {}

	public record PlaceRes(String number, int total, String paymentMethod, String redirectUrl) {}

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
