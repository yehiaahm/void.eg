package com.voidstore.api.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "coupons")
public class Coupon {

	/** PERCENT: value = 1–100 · FIXED: value in piastres · FREE_SHIPPING: value unused. */
	public enum Type { PERCENT, FIXED, FREE_SHIPPING }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String code;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Type type;

	@Column(nullable = false) private int value;
	@Column(name = "min_subtotal", nullable = false) private int minSubtotal;
	@Column(name = "starts_at") private Instant startsAt;
	@Column(name = "ends_at") private Instant endsAt;
	@Column(name = "max_uses") private Integer maxUses;
	@Column(name = "per_customer") private Integer perCustomer;
	@Column(name = "used_count", nullable = false) private int usedCount;
	@Column(nullable = false) private boolean active = true;

	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	public static String normalize(String code) {
		return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
	}

	/** Discount on the merchandise subtotal (free shipping is applied separately). */
	public int discountFor(int subtotal) {
		return switch (type) {
			case PERCENT -> Math.min(subtotal, Math.round(subtotal * Math.min(100, Math.max(0, value)) / 100f));
			case FIXED -> Math.min(subtotal, Math.max(0, value));
			case FREE_SHIPPING -> 0;
		};
	}

	public Long getId() { return id; }
	public String getCode() { return code; }
	public void setCode(String code) { this.code = normalize(code); }
	public Type getType() { return type; }
	public void setType(Type type) { this.type = type; }
	public int getValue() { return value; }
	public void setValue(int value) { this.value = value; }
	public int getMinSubtotal() { return minSubtotal; }
	public void setMinSubtotal(int v) { this.minSubtotal = v; }
	public Instant getStartsAt() { return startsAt; }
	public void setStartsAt(Instant v) { this.startsAt = v; }
	public Instant getEndsAt() { return endsAt; }
	public void setEndsAt(Instant v) { this.endsAt = v; }
	public Integer getMaxUses() { return maxUses; }
	public void setMaxUses(Integer v) { this.maxUses = v; }
	public Integer getPerCustomer() { return perCustomer; }
	public void setPerCustomer(Integer v) { this.perCustomer = v; }
	public int getUsedCount() { return usedCount; }
	public void incrementUsed() { this.usedCount++; }
	public void decrementUsed() { this.usedCount = Math.max(0, usedCount - 1); }
	public boolean isActive() { return active; }
	public void setActive(boolean active) { this.active = active; }
	public Instant getCreatedAt() { return createdAt; }
}
