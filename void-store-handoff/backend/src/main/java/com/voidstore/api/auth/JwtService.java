package com.voidstore.api.auth;

import com.voidstore.api.config.AppProperties;
import com.voidstore.api.user.User;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived access tokens (HS256). Subject = user id, claim `role`. */
@Service
public class JwtService {

	public static final String ISSUER = "void-api";

	private final JwtEncoder encoder;
	private final Duration ttl;

	public JwtService(JwtEncoder encoder, AppProperties props) {
		this.encoder = encoder;
		this.ttl = Duration.ofMinutes(props.jwt().accessMinutes());
	}

	public record AccessToken(String token, Instant expiresAt) {}

	public AccessToken issue(User user) {
		var now = Instant.now();
		var exp = now.plus(ttl);
		var claims = JwtClaimsSet.builder()
				.issuer(ISSUER)
				.subject(String.valueOf(user.getId()))
				.issuedAt(now)
				.expiresAt(exp)
				.claim("role", user.getRole().name())
				.claim("name", user.getName())
				.build();
		var header = JwsHeader.with(MacAlgorithm.HS256).build();
		return new AccessToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), exp);
	}
}
