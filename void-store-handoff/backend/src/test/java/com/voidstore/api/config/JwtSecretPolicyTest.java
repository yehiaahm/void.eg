package com.voidstore.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** VOID-01: the app must not be able to start with a missing, weak or known-placeholder JWT secret. */
class JwtSecretPolicyTest {

	/** The secret that used to be the default in application.yml — it is public, so it must be rejected. */
	private static final String FORMER_DEFAULT = "dev-only-secret-change-me-0123456789abcdef0123456789";

	static String strong() {
		byte[] b = new byte[48];
		new SecureRandom().nextBytes(b);
		return Base64.getEncoder().encodeToString(b);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   ", "\t\n"})
	void missingOrBlankSecretFailsStartup(String secret) {
		assertThatThrownBy(() -> JwtSecretPolicy.validate(secret))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("not set");
	}

	@Test
	void shortSecretFailsStartup() {
		assertThatThrownBy(() -> JwtSecretPolicy.validate("aB3$xY9!qW"))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("too short");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			FORMER_DEFAULT,
			"generate-48-random-bytes-base64-xxxxxxxxxxxx",      // backend/.env.example
			"change-me-to-something-long-and-unguessable-1",
			"test-secret-0123456789abcdef0123456789abcdef",      // the old test fixture
			"your-secret-goes-here-your-secret-goes-here",
			"MySuperSecretPasswordForTheJwtSigningKey2026"})
	void knownPlaceholdersAreRejectedEvenWhenLongEnough(String secret) {
		assertThat(secret.length()).isGreaterThanOrEqualTo(32);
		assertThatThrownBy(() -> JwtSecretPolicy.validate(secret)).isInstanceOf(IllegalStateException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "abababababababababababababababababababab",
			"QWERTYQWERTYQWERTYQWERTYQWERTYQWERTYQWERTY"})
	void lowEntropySecretsAreRejected(String secret) {
		assertThatThrownBy(() -> JwtSecretPolicy.validate(secret))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("random");
	}

	@Test
	void surroundingWhitespaceIsRejected() {
		assertThatThrownBy(() -> JwtSecretPolicy.validate(" " + strong()))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("whitespace");
	}

	@Test
	void freshRandomSecretsAreAccepted() {
		for (int i = 0; i < 200; i++) JwtSecretPolicy.validate(strong());
		JwtSecretPolicy.validate("k9Vt2mQx7LpR4sYw1ZcN8bHf3JdG6aUe0TqXiOvE5yMrB");
	}

	@Test
	void errorMessagesNeverContainTheSecret() {
		String candidate = "change-me-" + strong();
		assertThatThrownBy(() -> JwtSecretPolicy.validate(candidate))
				.hasMessageNotContaining(candidate).hasMessageNotContaining(candidate.substring(10, 30));
	}
}
