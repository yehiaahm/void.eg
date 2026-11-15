package com.voidstore.api.config;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Startup check for the JWT signing secret (VOID-01). The application refuses to start unless the
 * secret is present, long enough, not a known placeholder and not obviously low-entropy.
 * Error messages never contain the secret itself.
 */
public final class JwtSecretPolicy {

	/** HS256 wants at least 256 bits of key material. */
	public static final int MIN_BYTES = 32;

	private static final double MIN_ENTROPY_BITS_PER_CHAR = 3.0;
	private static final int MIN_DISTINCT_CHARS = 12;

	/** Fragments of secrets that ship in the repository, docs or examples. Matched case-insensitively. */
	private static final List<String> KNOWN_PLACEHOLDERS = List.of(
			"dev-only", "change-me", "changeme", "change_me", "generate-", "generate_", "placeholder", "your-secret",
			"your_secret", "yoursecret", "example", "test-secret", "insecure", "default", "0123456789", "abcdefghij",
			"password", "random-bytes");

	private JwtSecretPolicy() {}

	/** @throws IllegalStateException when the secret must not be used; the message names the rule, never the value. */
	public static void validate(String secret) {
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException(
					"JWT_SECRET (void.jwt.secret) is not set. Generate one with `openssl rand -base64 48` and provide it "
							+ "through the environment (production) or backend/local.properties (development).");
		}
		if (!secret.equals(secret.strip())) {
			throw new IllegalStateException("JWT_SECRET must not start or end with whitespace.");
		}
		if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_BYTES) {
			throw new IllegalStateException("JWT_SECRET is too short: it must be at least " + MIN_BYTES + " bytes.");
		}
		String lower = secret.toLowerCase(Locale.ROOT);
		for (String marker : KNOWN_PLACEHOLDERS) {
			if (lower.contains(marker)) {
				throw new IllegalStateException(
						"JWT_SECRET looks like a placeholder or example value. Generate a fresh random secret.");
			}
		}
		if (distinctChars(secret) < MIN_DISTINCT_CHARS || entropyBitsPerChar(secret) < MIN_ENTROPY_BITS_PER_CHAR) {
			throw new IllegalStateException("JWT_SECRET is not random enough. Generate one with `openssl rand -base64 48`.");
		}
	}

	static int distinctChars(String s) {
		return (int) s.chars().distinct().count();
	}

	/** Shannon entropy of the character distribution, in bits per character. */
	static double entropyBitsPerChar(String s) {
		Map<Integer, Integer> counts = new HashMap<>();
		s.chars().forEach(c -> counts.merge(c, 1, Integer::sum));
		double n = s.length();
		double h = 0;
		for (int c : counts.values()) {
			double p = c / n;
			h -= p * (Math.log(p) / Math.log(2));
		}
		return h;
	}
}
