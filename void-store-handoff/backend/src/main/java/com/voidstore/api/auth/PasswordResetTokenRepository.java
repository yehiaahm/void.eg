package com.voidstore.api.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<AuthToken.PasswordReset, Long> {
	Optional<AuthToken.PasswordReset> findByTokenHash(String tokenHash);
}
