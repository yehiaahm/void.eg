package com.voidstore.api.returns;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class ReturnDtos {
	private ReturnDtos() {}

	public record ItemReq(@NotNull Long itemId, @Min(1) @Max(10) int qty, @Size(max = 8) String exchangeSize) {}

	public record CreateReq(
			@NotBlank @Size(max = 16) String type,
			@NotBlank @Size(max = 24) String reason,
			@Size(max = 1000) String note,
			@NotEmpty @Size(max = 30) List<@Valid ItemReq> items) {}

	/** Guest version: proves ownership with the order number + the private tracking token from the e-mailed link. */
	public record GuestCreateReq(
			@NotBlank @Size(max = 20) String number,
			@NotBlank @Size(max = 128) String token,
			@NotBlank @Size(max = 16) String type,
			@NotBlank @Size(max = 24) String reason,
			@Size(max = 1000) String note,
			@NotEmpty @Size(max = 30) List<@Valid ItemReq> items) {}

	public record ItemDto(long orderItemId, String slug, String name, String size, String image, int unitPrice, int qty,
			String exchangeSize) {}

	public record EventDto(String fromStatus, String toStatus, String note, String actor, Instant at) {}

	/** Customer-facing request. */
	public record ReturnDto(String number, String type, String status, String reason, String note, Integer refundAmount,
			Instant createdAt, List<ItemDto> items, List<EventDto> timeline) {}

	static List<ItemDto> items(ReturnRequest r) {
		return r.getItems().stream().map(i -> {
			var oi = i.getOrderItem();
			return new ItemDto(oi.getId(), oi.getSlug(), oi.getName(), oi.getSize(), oi.getImageUrl(), oi.getUnitPrice(),
					i.getQty(), i.getExchangeSize());
		}).toList();
	}

	public static ReturnDto of(ReturnRequest r) {
		return new ReturnDto(r.getNumber(), r.getType().name(), r.getStatus().name(), r.getReason().name(),
				r.getCustomerNote(), r.getRefundAmount(), r.getCreatedAt(), items(r),
				// customers see the steps, not the staff's internal notes
				r.getEvents().stream().map(e -> new EventDto(e.getFromStatus() == null ? null : e.getFromStatus().name(),
						e.getToStatus().name(), "", "", e.getCreatedAt())).toList());
	}
}
