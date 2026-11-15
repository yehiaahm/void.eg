package com.voidstore.api.order;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Unguessable per-order access tokens for guests (VOID-04).
 *
 * <p>An order number is sequential, and a phone number is not a secret, so neither can authorise a guest to see
 * personal data or to cancel / return an order. The token is {@code HMAC-SHA256(key, "order-track:v1:" + number)},
 * 256 bits, derived from a key that is itself derived from the server secret. Nothing is stored: the same token is
 * recomputed for the confirmation and status e-mails and verified in constant time. Without the server secret a
 * token cannot be forged for another order, and rotating the secret invalidates all links.
 */
@Component
public class TrackingTokens {

	private final SecretKey key;

	public TrackingTokens(SecretKey jwtKey) {
		// domain-separated sub-key: the raw JWT key is never used directly for tracking tokens
		this.key = new SecretKeySpec(hmac(jwtKey, "void-tracking-key:v1"), "HmacSHA256");
	}

	/** The token for an order number (what goes into the e-mailed link). */
	public String forOrder(String orderNumber) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(key, "order-track:v1:" + orderNumber));
	}

	/** Constant-time check of a presented token. */
	public boolean matches(String orderNumber, String presented) {
		if (orderNumber == null || presented == null || presented.length() > 128) return false;
		byte[] expected = forOrder(orderNumber).getBytes(StandardCharsets.UTF_8);
		return MessageDigest.isEqual(expected, presented.trim().getBytes(StandardCharsets.UTF_8));
	}

	private static byte[] hmac(SecretKey k, String message) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(k);
			return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException(e);
		}
	}
}
