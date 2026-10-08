package com.voidstore.api.waitlist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A visitor who left their details while the store was closed. */
@Entity
@Table(name = "waitlist_entries")
public class WaitlistEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false, length = 16)
	private String phone;

	@Column(length = 190)
	private String email;

	@Column(nullable = false, length = 1000)
	private String notes = "";

	@Column(nullable = false, length = 2)
	private String lang = "en";

	@Column(nullable = false)
	private boolean contacted;

	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private Instant updatedAt;

	public Long getId() { return id; }
	public String getName() { return name; }
	public void setName(String v) { this.name = v; }
	public String getPhone() { return phone; }
	public void setPhone(String v) { this.phone = v; }
	public String getEmail() { return email; }
	public void setEmail(String v) { this.email = v; }
	public String getNotes() { return notes; }
	public void setNotes(String v) { this.notes = v; }
	public String getLang() { return lang; }
	public void setLang(String v) { this.lang = v; }
	public boolean isContacted() { return contacted; }
	public void setContacted(boolean v) { this.contacted = v; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}
