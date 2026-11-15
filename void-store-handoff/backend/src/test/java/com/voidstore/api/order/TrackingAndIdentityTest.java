package com.voidstore.api.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.voidstore.api.order.OrderDtos.EventDto;
import com.voidstore.api.order.OrderDtos.ItemDto;
import com.voidstore.api.order.OrderDtos.OrderDto;
import com.voidstore.api.returns.ReturnDtos;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** VOID-04 (tracking tokens, masked guest views) and VOID-05 (customer identity) building blocks. */
class TrackingAndIdentityTest {

	private static TrackingTokens tokens(String secret) {
		return new TrackingTokens(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
	}

	private final TrackingTokens tokens = tokens("k9Vt2mQx7LpR4sYw1ZcN8bHf3JdG6aUe0TqXiOvE5yMrB");

	@Test
	void tokenIsLongDeterministicAndPerOrder() {
		String t = tokens.forOrder("VOID-001001");
		assertThat(t).hasSize(43).matches("[A-Za-z0-9_-]+").isEqualTo(tokens.forOrder("VOID-001001"));
		assertThat(tokens.forOrder("VOID-001002")).isNotEqualTo(t); // sequential numbers do not give sequential tokens
	}

	@Test
	void tokenOfAnotherOrderOrAnotherServerSecretIsRejected() {
		String t = tokens.forOrder("VOID-001001");
		assertThat(tokens.matches("VOID-001001", t)).isTrue();
		assertThat(tokens.matches("VOID-001001", " " + t + " ")).isTrue(); // e-mail clients may add spaces
		assertThat(tokens.matches("VOID-001002", t)).isFalse();
		assertThat(tokens.matches("VOID-001001", tokens.forOrder("VOID-001002"))).isFalse();
		assertThat(tokens("zZ1xY2wV3uT4sR5qP6oN7mL8kJ9iH0gFeDcBaA1b2C3d4").matches("VOID-001001", t)).isFalse();
	}

	@Test
	void missingOrOversizedTokensAreRejected() {
		assertThat(tokens.matches("VOID-001001", null)).isFalse();
		assertThat(tokens.matches("VOID-001001", "")).isFalse();
		assertThat(tokens.matches(null, "x")).isFalse();
		assertThat(tokens.matches("VOID-001001", "a".repeat(5000))).isFalse();
		assertThat(tokens.matches("VOID-001001", "VOID-001001")).isFalse(); // the order number is not a token
	}

	// ---- masked guest views

	private static OrderDto order() {
		var item = new ItemDto(1, "tee", "Tee", "M", null, null, 1000, 1, 1000, 0, 1);
		var ret = new ReturnDtos.ReturnDto("RET-00101", "EXCHANGE", "REQUESTED", "SIZE", "private note", null, Instant.now(),
				List.of(), List.of());
		return new OrderDto("VOID-001001", "NEW", "COD", "PENDING", Instant.now(), "Mona Ahmed", "01001234567",
				"mona.ahmed@example.com", "cairo", "Nasr City", "1 Test St", "B2", "ring twice", List.of(item), 1000, 100, 50, 950,
				"TEN", List.<EventDto>of(), true, Instant.now(), List.of(ret));
	}

	@Test
	void phoneOnlyViewIsReadOnlyAndHidesPersonalData() {
		var v = OrderDtos.forGuest(order(), false);
		assertThat(v.number()).isEqualTo("VOID-001001");
		assertThat(v.status()).isEqualTo("NEW");
		assertThat(v.total()).isEqualTo(950);
		assertThat(v.name()).isEqualTo("M. A.");
		assertThat(v.phone()).isEqualTo("010******67").doesNotContain("1234");
		assertThat(v.email()).isEqualTo("m***@example.com");
		assertThat(v.street()).isEmpty();
		assertThat(v.city()).isEmpty();
		assertThat(v.building()).isEmpty();
		assertThat(v.addressNotes()).isEmpty();
		assertThat(v.couponCode()).isNull();
		assertThat(v.canCancel()).isFalse();
		assertThat(v.returnUntil()).isNull();
		assertThat(v.items().get(0).returnableQty()).isZero();
		assertThat(v.returns().get(0).note()).isEmpty();
	}

	@Test
	void tokenViewKeepsDeliveryDetailsButMasksPhoneAndEmail() {
		var v = OrderDtos.forGuest(order(), true);
		assertThat(v.name()).isEqualTo("Mona Ahmed");
		assertThat(v.street()).isEqualTo("1 Test St");
		assertThat(v.phone()).isEqualTo("010******67");
		assertThat(v.email()).isEqualTo("m***@example.com");
		assertThat(v.canCancel()).isTrue();
		assertThat(v.items().get(0).returnableQty()).isEqualTo(1);
	}

	@Test
	void maskingHandlesMissingValues() {
		assertThat(OrderDtos.maskPhone(null)).isEmpty();
		assertThat(OrderDtos.maskPhone("123")).isEmpty();
		assertThat(OrderDtos.maskEmail(null)).isEmpty();
		assertThat(OrderDtos.maskEmail("no-at-sign")).isEmpty();
		assertThat(OrderDtos.initials("  ")).isEmpty();
		assertThat(OrderDtos.initials("منى أحمد")).isEqualTo("م. أ.");
	}

	// ---- customer identity (coupon limits)

	@Test
	void canonicalEmailCollapsesTagsCaseAndGmailDots() {
		assertThat(CustomerIdentity.canonicalEmail("J.Doe+promo@Gmail.com")).isEqualTo("jdoe@gmail.com");
		assertThat(CustomerIdentity.canonicalEmail("jdoe@googlemail.com")).isEqualTo("jdoe@gmail.com");
		assertThat(CustomerIdentity.canonicalEmail("buyer+1@example.com")).isEqualTo("buyer@example.com");
		assertThat(CustomerIdentity.canonicalEmail("buyer+2@example.com")).isEqualTo("buyer@example.com");
		assertThat(CustomerIdentity.canonicalEmail("first.last@example.com")).isEqualTo("first.last@example.com"); // dots matter elsewhere
		assertThat(CustomerIdentity.canonicalEmail(null)).isNull();
	}

	@Test
	void addressKeyIgnoresCaseSpacingAndPunctuationButNotTheAddress() {
		String a = CustomerIdentity.addressKey("cairo", "Nasr City", "1 Test St.", "B2");
		assertThat(CustomerIdentity.addressKey("CAIRO", " nasr  city ", "1, test st", "b-2")).isEqualTo(a);
		assertThat(CustomerIdentity.addressKey("cairo", "Nasr City", "2 Test St", "B2")).isNotEqualTo(a);
		assertThat(CustomerIdentity.addressKey("cairo", "مدينة نصر", "شارع التحرير", ""))
				.isNotEqualTo(CustomerIdentity.addressKey("cairo", "مدينة نصر", "شارع الهرم", ""));
		assertThat(a).hasSize(64);
	}
}
