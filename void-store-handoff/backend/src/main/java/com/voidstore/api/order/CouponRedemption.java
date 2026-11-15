package com.voidstore.api.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "coupon_redemptions")
public class CouponRedemption {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "coupon_id")
	private Coupon coupon;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id")
	private Order order;

	/** Canonical e-mail (see CustomerIdentity); older rows hold the plain lower-cased address. */
	@Column(nullable = false)
	private String email;

	private String phone;

	@Column(name = "user_id")
	private Long userId;

	@Column(name = "address_key")
	private String addressKey;

	protected CouponRedemption() {}

	public CouponRedemption(Coupon coupon, Order order, String email, String phone, Long userId, String addressKey) {
		this.coupon = coupon;
		this.order = order;
		this.email = email;
		this.phone = phone;
		this.userId = userId;
		this.addressKey = addressKey;
	}

	public Coupon getCoupon() { return coupon; }
	public Order getOrder() { return order; }
}
