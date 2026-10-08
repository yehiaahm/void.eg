package com.voidstore.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

	/** Ordered by power: each role can do everything the roles before it can. */
	public enum Role {
		CUSTOMER, STAFF, ADMIN, OWNER;

		public boolean atLeast(Role other) {
			return ordinal() >= other.ordinal();
		}

		public boolean isStaff() {
			return atLeast(STAFF);
		}
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(nullable = false)
	private String name;

	private String phone;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role = Role.CUSTOMER;

	@Column(nullable = false)
	private boolean enabled = true;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	protected User() {}

	public User(String email, String passwordHash, String name, Role role) {
		this.email = normalizeEmail(email);
		this.passwordHash = passwordHash;
		this.name = name;
		this.role = role;
	}

	public static String normalizeEmail(String email) {
		return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
	}

	public Long getId() { return id; }
	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = normalizeEmail(email); }
	public String getPasswordHash() { return passwordHash; }
	public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getPhone() { return phone; }
	public void setPhone(String phone) { this.phone = phone; }
	public Role getRole() { return role; }
	public void setRole(Role role) { this.role = role; }
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
	public Instant getLastLoginAt() { return lastLoginAt; }
	public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
	public Instant getCreatedAt() { return createdAt; }
}
