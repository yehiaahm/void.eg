package com.voidstore.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Saved delivery address of a customer account. */
@Entity
@Table(name = "addresses")
public class Address {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	@Column(nullable = false) private String name;
	@Column(nullable = false) private String phone;
	@Column(nullable = false) private String governorate;
	@Column(nullable = false) private String city;
	@Column(nullable = false) private String street;
	@Column(nullable = false) private String building = "";
	@Column(nullable = false) private String notes = "";
	@Column(name = "is_default", nullable = false) private boolean isDefault;

	protected Address() {}

	public Address(User user) {
		this.user = user;
	}

	public Long getId() { return id; }
	public User getUser() { return user; }
	public String getName() { return name; }
	public void setName(String v) { this.name = v; }
	public String getPhone() { return phone; }
	public void setPhone(String v) { this.phone = v; }
	public String getGovernorate() { return governorate; }
	public void setGovernorate(String v) { this.governorate = v; }
	public String getCity() { return city; }
	public void setCity(String v) { this.city = v; }
	public String getStreet() { return street; }
	public void setStreet(String v) { this.street = v; }
	public String getBuilding() { return building; }
	public void setBuilding(String v) { this.building = v == null ? "" : v; }
	public String getNotes() { return notes; }
	public void setNotes(String v) { this.notes = v == null ? "" : v; }
	public boolean isDefault() { return isDefault; }
	public void setDefault(boolean v) { this.isDefault = v; }
}
