package com.voidstore.api.shipping;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One Egyptian governorate with its delivery fee. Disabled zones are hidden at checkout. */
@Entity
@Table(name = "shipping_zones")
public class ShippingZone {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String code;

	@Column(name = "name_en", nullable = false) private String nameEn;
	@Column(name = "name_ar", nullable = false) private String nameAr;

	/** Piastres; null = not set yet, so the zone is unavailable. */
	private Integer fee;

	@Column(name = "eta_days", nullable = false)
	private String etaDays = "";

	@Column(nullable = false)
	private boolean enabled;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	public boolean isAvailable() {
		return enabled && fee != null;
	}

	public Long getId() { return id; }
	public String getCode() { return code; }
	public String getNameEn() { return nameEn; }
	public String getNameAr() { return nameAr; }
	public Integer getFee() { return fee; }
	public void setFee(Integer fee) { this.fee = fee; }
	public String getEtaDays() { return etaDays; }
	public void setEtaDays(String v) { this.etaDays = v == null ? "" : v.trim(); }
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
	public int getSortOrder() { return sortOrder; }
}
