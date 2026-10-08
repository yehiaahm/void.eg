package com.voidstore.api.contact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A message a visitor sent from the Contact page. */
@Entity
@Table(name = "contact_messages")
public class ContactMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false, length = 190)
	private String email;

	@Column(length = 32)
	private String phone;

	@Column(nullable = false, length = 2000)
	private String message;

	@Column(nullable = false, length = 2)
	private String lang = "en";

	@Column(nullable = false)
	private boolean handled;

	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private Instant updatedAt;

	public Long getId() { return id; }
	public String getName() { return name; }
	public void setName(String v) { this.name = v; }
	public String getEmail() { return email; }
	public void setEmail(String v) { this.email = v; }
	public String getPhone() { return phone; }
	public void setPhone(String v) { this.phone = v; }
	public String getMessage() { return message; }
	public void setMessage(String v) { this.message = v; }
	public String getLang() { return lang; }
	public void setLang(String v) { this.lang = v; }
	public boolean isHandled() { return handled; }
	public void setHandled(boolean v) { this.handled = v; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}
