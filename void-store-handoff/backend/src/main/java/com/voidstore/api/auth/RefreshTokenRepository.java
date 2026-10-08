package com.voidstore.api.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<AuthToken.Refresh, Long> {
	Optional<AuthToken.Refresh> findByTokenHash(String tokenHash);

	@Modifying
	@Query("update RefreshToken t set t.revoked = true where t.user.id = :userId and t.revoked = false")
	int revokeAllForUser(Long userId);

	@Modifying
	@Query("delete from RefreshToken t where t.expiresAt < CURRENT_TIMESTAMP or t.revoked = true")
	int purgeStale();
}
