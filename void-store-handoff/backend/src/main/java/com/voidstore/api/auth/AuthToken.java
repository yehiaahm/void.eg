package com.voidstore.api.auth;

import com.voidstore.api.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Table;
import java.time.Instant;

/** Opaque tokens are stored only as SHA-256 hashes. */
@MappedSuperclass
public abstract class AuthToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	@Column(name = "token_hash", nullable = false, unique = true)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	protected AuthToken() {}

	protected AuthToken(User user, String tokenHash, Instant expiresAt) {
		this.user = user;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	public Long getId() { return id; }
	public User getUser() { return user; }
	public String getTokenHash() { return tokenHash; }
	public Instant getExpiresAt() { return expiresAt; }
	public boolean isExpired() { return Instant.now().isAfter(expiresAt); }

	@Entity(name = "RefreshToken")
	@Table(name = "refresh_tokens")
	public static class Refresh extends AuthToken {
		@Column(nullable = false)
		private boolean revoked;

		protected Refresh() {}

		public Refresh(User user, String hash, Instant expiresAt) {
			super(user, hash, expiresAt);
		}

		public boolean isRevoked() { return revoked; }
		public void revoke() { this.revoked = true; }
	}

	@Entity(name = "PasswordResetToken")
	@Table(name = "password_reset_tokens")
	public static class PasswordReset extends AuthToken {
		@Column(nullable = false)
		private boolean used;

		protected PasswordReset() {}

		public PasswordReset(User user, String hash, Instant expiresAt) {
			super(user, hash, expiresAt);
		}

		public boolean isUsed() { return used; }
		public void markUsed() { this.used = true; }
	}
}
