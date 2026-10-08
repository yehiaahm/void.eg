package com.voidstore.api.auth;

import com.voidstore.api.config.AppProperties;
import com.voidstore.api.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	public static final String COOKIE = "void_rt";
	private static final String COOKIE_PATH = "/api/v1/auth";

	public record RegisterReq(
			@NotBlank @Size(max = 120) String name,
			@NotBlank @Email @Size(max = 190) String email,
			@NotBlank @Size(min = 8, max = 100) String password,
			@Size(max = 32) String phone) {}

	public record LoginReq(@NotBlank @Email String email, @NotBlank String password) {}

	public record ForgotReq(@NotBlank @Email String email, String lang) {}

	public record ResetReq(@NotBlank String token, @NotBlank @Size(min = 8, max = 100) String password) {}

	public record UserDto(long id, String email, String name, String phone, String role) {
		public static UserDto of(User u) {
			return new UserDto(u.getId(), u.getEmail(), u.getName(), u.getPhone(), u.getRole().name());
		}
	}

	public record SessionDto(String accessToken, Instant expiresAt, UserDto user) {}

	private final AuthService auth;
	private final RateLimiter limiter;
	private final AppProperties props;

	public AuthController(AuthService auth, RateLimiter limiter, AppProperties props) {
		this.auth = auth;
		this.limiter = limiter;
		this.props = props;
	}

	@PostMapping("/register")
	public ResponseEntity<SessionDto> register(@Valid @RequestBody RegisterReq req, HttpServletRequest http) {
		limiter.check("register:" + http.getRemoteAddr(), 10, Duration.ofHours(1));
		return session(auth.register(req.name(), req.email(), req.password(), req.phone()));
	}

	@PostMapping("/login")
	public ResponseEntity<SessionDto> login(@Valid @RequestBody LoginReq req, HttpServletRequest http) {
		String key = "login:" + http.getRemoteAddr() + ":" + User.normalizeEmail(req.email());
		limiter.check(key, 8, Duration.ofMinutes(15));
		var s = auth.login(req.email(), req.password());
		limiter.reset(key);
		return session(s);
	}

	@PostMapping("/refresh")
	public ResponseEntity<SessionDto> refresh(@CookieValue(name = COOKIE, required = false) String token) {
		return session(auth.refresh(token));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@CookieValue(name = COOKIE, required = false) String token, HttpServletResponse res) {
		auth.logout(token);
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
	}

	@PostMapping("/forgot")
	public ResponseEntity<Void> forgot(@Valid @RequestBody ForgotReq req, HttpServletRequest http) {
		limiter.check("forgot:" + http.getRemoteAddr(), 5, Duration.ofHours(1));
		auth.forgotPassword(req.email(), req.lang());
		return ResponseEntity.noContent().build(); // same answer whether or not the account exists
	}

	@PostMapping("/reset")
	public ResponseEntity<Void> reset(@Valid @RequestBody ResetReq req, HttpServletRequest http) {
		limiter.check("reset:" + http.getRemoteAddr(), 10, Duration.ofHours(1));
		auth.resetPassword(req.token(), req.password());
		return ResponseEntity.noContent().build();
	}

	private ResponseEntity<SessionDto> session(AuthService.Session s) {
		var c = cookie(s.refreshToken(), Duration.ofDays(props.jwt().refreshDays()));
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, c.toString())
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(new SessionDto(s.access().token(), s.access().expiresAt(), UserDto.of(s.user())));
	}

	private ResponseCookie cookie(String value, Duration maxAge) {
		return ResponseCookie.from(COOKIE, value)
				.httpOnly(true)
				.secure(props.jwt().secureCookie())
				.sameSite("Strict")
				.path(COOKIE_PATH)
				.maxAge(maxAge)
				.build();
	}
}
