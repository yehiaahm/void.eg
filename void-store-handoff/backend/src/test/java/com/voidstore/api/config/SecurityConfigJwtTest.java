package com.voidstore.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.voidstore.api.auth.JwtService;
import com.voidstore.api.user.User;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** VOID-01: wires the real SecurityConfig beans and proves forged OWNER tokens are rejected. */
class SecurityConfigJwtTest {

	private static final String FORMER_DEFAULT = "dev-only-secret-change-me-0123456789abcdef0123456789";

	private final SecurityConfig config = new SecurityConfig();

	private static AppProperties props(String secret) {
		return new AppProperties("http://localhost:5173", "uploads", "VOID <o@void.local>",
				new AppProperties.Jwt(secret, 15, 30, false), null);
	}

	private static String sign(String secret, String role, Instant exp) {
		SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		var claims = JwtClaimsSet.builder().issuer(JwtService.ISSUER).subject("1").issuedAt(exp.minusSeconds(600))
				.expiresAt(exp).claim("role", role).claim("name", "synthetic").build();
		return new NimbusJwtEncoder(new ImmutableSecret<>(key))
				.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	@Test
	void applicationCannotBuildAKeyFromTheFormerPublicDefault() {
		assertThatThrownBy(() -> config.jwtKey(props(FORMER_DEFAULT))).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void applicationCannotBuildAKeyFromAMissingSecret() {
		assertThatThrownBy(() -> config.jwtKey(props(null))).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> config.jwtKey(props(""))).isInstanceOf(IllegalStateException.class); // JWT_SECRET unset → ""
	}

	@Test
	void ownerTokenForgedWithTheFormerDefaultSecretIsRejected() {
		String real = JwtSecretPolicyTest.strong();
		JwtDecoder decoder = config.jwtDecoder(config.jwtKey(props(real)));
		String forged = sign(FORMER_DEFAULT, "OWNER", Instant.now().plusSeconds(600));
		assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
	}

	@Test
	void tokenSignedByAnotherRandomSecretIsRejectedButOurOwnIsAccepted() {
		String real = JwtSecretPolicyTest.strong();
		JwtDecoder decoder = config.jwtDecoder(config.jwtKey(props(real)));
		assertThatThrownBy(() -> decoder.decode(sign(JwtSecretPolicyTest.strong(), "OWNER", Instant.now().plusSeconds(600))))
				.isInstanceOf(JwtException.class);

		var service = new JwtService(config.jwtEncoder(config.jwtKey(props(real))), props(real));
		var user = new User("synthetic@void.local", "x", "Synthetic", User.Role.STAFF);
		var jwt = decoder.decode(service.issue(user).token());
		assertThat(jwt.getClaimAsString("role")).isEqualTo("STAFF");
	}

	@Test
	void expiredTokensAreRejected() {
		String real = JwtSecretPolicyTest.strong();
		JwtDecoder decoder = config.jwtDecoder(config.jwtKey(props(real)));
		assertThatThrownBy(() -> decoder.decode(sign(real, "OWNER", Instant.now().minusSeconds(120)))).isInstanceOf(JwtException.class);
	}
}
