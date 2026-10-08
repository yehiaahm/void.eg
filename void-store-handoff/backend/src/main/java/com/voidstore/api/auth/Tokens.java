package com.voidstore.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Random opaque tokens and their SHA-256 hashes. */
public final class Tokens {
	private static final SecureRandom RANDOM = new SecureRandom();

	private Tokens() {}

	public static String random() {
		byte[] b = new byte[32];
		RANDOM.nextBytes(b);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
	}

	public static String sha256(String value) {
		try {
			var md = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
