package com.voidstore.api.order;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/**
 * What counts as "the same customer" for per-customer coupon limits (VOID-05). A typed e-mail alone is trivial to
 * change, so a redemption is matched on any of: signed-in account, phone number, canonical e-mail, delivery address.
 */
public final class CustomerIdentity {

	private static final Set<String> GOOGLE = Set.of("gmail.com", "googlemail.com");

	private CustomerIdentity() {}

	/**
	 * Lower-cases and removes {@code +tag} sub-addressing; for Gmail also removes dots (they are ignored there), so
	 * {@code J.Doe+x@gmail.com} and {@code jdoe@googlemail.com} are one mailbox.
	 */
	public static String canonicalEmail(String email) {
		if (email == null) return null;
		String e = email.trim().toLowerCase(Locale.ROOT);
		int at = e.lastIndexOf('@');
		if (at <= 0) return e;
		String local = e.substring(0, at);
		String domain = e.substring(at + 1);
		int plus = local.indexOf('+');
		if (plus > 0) local = local.substring(0, plus);
		if (GOOGLE.contains(domain)) {
			local = local.replace(".", "");
			domain = "gmail.com";
		}
		return local + "@" + domain;
	}

	/** SHA-256 of the delivery address reduced to letters and digits, so spacing/punctuation tricks do not help. */
	public static String addressKey(String governorate, String city, String street, String building) {
		String raw = String.join("|", norm(governorate), norm(city), norm(street), norm(building));
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String norm(String s) {
		if (s == null) return "";
		return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
	}
}
