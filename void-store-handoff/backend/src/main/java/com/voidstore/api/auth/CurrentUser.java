package com.voidstore.api.auth;

import com.voidstore.api.common.ApiException;
import com.voidstore.api.user.User;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Reads the signed-in user from the JWT in the security context. */
public final class CurrentUser {
	private CurrentUser() {}

	public record Principal(long id, User.Role role, String name) {}

	public static Optional<Principal> get() {
		if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken t) {
			var jwt = t.getToken();
			return Optional.of(new Principal(Long.parseLong(jwt.getSubject()),
					User.Role.valueOf(jwt.getClaimAsString("role")), jwt.getClaimAsString("name")));
		}
		return Optional.empty();
	}

	public static Principal require() {
		return get().orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Sign in required"));
	}

	public static Principal requireRole(User.Role min) {
		var p = require();
		if (!p.role().atLeast(min)) throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "Not allowed");
		return p;
	}
}
