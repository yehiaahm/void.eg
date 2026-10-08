package com.voidstore.api.auth;

import com.voidstore.api.common.ApiException;
import com.voidstore.api.config.AppProperties;
import com.voidstore.api.mail.EmailLayout;
import com.voidstore.api.mail.MailService;
import com.voidstore.api.user.User;
import com.voidstore.api.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final UserRepository users;
	private final RefreshTokenRepository refreshTokens;
	private final PasswordResetTokenRepository resetTokens;
	private final PasswordEncoder encoder;
	private final JwtService jwt;
	private final MailService mail;
	private final AppProperties props;
	/** Compared against when the email is unknown, so response time does not reveal which accounts exist. */
	private final String dummyHash;

	public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordResetTokenRepository resetTokens,
			PasswordEncoder encoder, JwtService jwt, MailService mail, AppProperties props) {
		this.users = users;
		this.refreshTokens = refreshTokens;
		this.resetTokens = resetTokens;
		this.encoder = encoder;
		this.jwt = jwt;
		this.mail = mail;
		this.props = props;
		this.dummyHash = encoder.encode(Tokens.random());
	}

	/** Access token + the raw refresh token (goes into the httpOnly cookie, never into JSON). */
	public record Session(JwtService.AccessToken access, String refreshToken, User user) {}

	@Transactional
	public Session register(String name, String email, String password, String phone) {
		String normalized = User.normalizeEmail(email);
		if (users.existsByEmail(normalized)) throw ApiException.conflict("email_taken", "An account with this email already exists");
		var u = new User(normalized, encoder.encode(password), name.trim(), User.Role.CUSTOMER);
		u.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
		users.save(u);
		return startSession(u);
	}

	@Transactional
	public Session login(String email, String password) {
		var user = users.findByEmail(User.normalizeEmail(email)).orElse(null);
		boolean ok = encoder.matches(password, user == null ? dummyHash : user.getPasswordHash());
		if (user == null || !ok) throw new ApiException(HttpStatus.UNAUTHORIZED, "bad_credentials", "Wrong email or password");
		if (!user.isEnabled()) throw new ApiException(HttpStatus.FORBIDDEN, "disabled", "This account is disabled");
		user.setLastLoginAt(Instant.now());
		return startSession(user);
	}

	/** Rotates the refresh token. Re-use of an already-rotated token revokes every session of that user. */
	@Transactional(noRollbackFor = ApiException.class)
	public Session refresh(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) throw unauthorized();
		var t = refreshTokens.findByTokenHash(Tokens.sha256(rawToken)).orElseThrow(AuthService::unauthorized);
		if (t.isRevoked()) {
			refreshTokens.revokeAllForUser(t.getUser().getId());
			throw unauthorized();
		}
		if (t.isExpired() || !t.getUser().isEnabled()) throw unauthorized();
		t.revoke();
		return startSession(t.getUser());
	}

	@Transactional
	public void logout(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) return;
		refreshTokens.findByTokenHash(Tokens.sha256(rawToken)).ifPresent(AuthToken.Refresh::revoke);
	}

	@Transactional
	public void forgotPassword(String email, String lang) {
		users.findByEmail(User.normalizeEmail(email)).filter(User::isEnabled).ifPresent(u -> {
			String raw = Tokens.random();
			resetTokens.save(new AuthToken.PasswordReset(u, Tokens.sha256(raw), Instant.now().plus(Duration.ofHours(1))));
			boolean ar = "ar".equals(lang);
			String link = props.frontendUrl().split(",")[0].trim() + "/" + (ar ? "ar" : "en") + "/account/reset?token=" + raw;
			String body = ar
					? "<p>أهلاً " + EmailLayout.esc(u.getName()) + "،</p><p>وصلنا طلب لتغيير كلمة المرور. الرابط صالح لمدة ساعة.</p>"
							+ EmailLayout.button(link, "تغيير كلمة المرور")
							+ "<p style=\"color:#A7A49D\">لو ماطلبتش ده، تجاهل الرسالة.</p>"
					: "<p>Hi " + EmailLayout.esc(u.getName()) + ",</p><p>We received a request to reset your password. The link is valid for one hour.</p>"
							+ EmailLayout.button(link, "Reset password")
							+ "<p style=\"color:#A7A49D\">If you didn't ask for this, you can ignore this email.</p>";
			mail.send(u.getEmail(), ar ? "VOID — تغيير كلمة المرور" : "VOID — Reset your password", EmailLayout.wrap(lang, body));
		});
	}

	@Transactional
	public void resetPassword(String rawToken, String newPassword) {
		var t = resetTokens.findByTokenHash(Tokens.sha256(rawToken == null ? "" : rawToken))
				.filter(x -> !x.isUsed() && !x.isExpired())
				.orElseThrow(() -> ApiException.badRequest("invalid_token", "This link is invalid or has expired"));
		t.markUsed();
		var u = t.getUser();
		u.setPasswordHash(encoder.encode(newPassword));
		refreshTokens.revokeAllForUser(u.getId());
	}

	@Transactional
	public void changePassword(long userId, String current, String next) {
		var u = users.findById(userId).orElseThrow(AuthService::unauthorized);
		if (!encoder.matches(current, u.getPasswordHash())) {
			throw ApiException.badRequest("bad_credentials", "Current password is wrong");
		}
		u.setPasswordHash(encoder.encode(next));
	}

	private Session startSession(User u) {
		String raw = Tokens.random();
		refreshTokens.save(new AuthToken.Refresh(u, Tokens.sha256(raw), Instant.now().plus(Duration.ofDays(props.jwt().refreshDays()))));
		return new Session(jwt.issue(u), raw, u);
	}

	@Scheduled(cron = "0 17 3 * * *")
	@Transactional
	public void purgeStaleTokens() {
		refreshTokens.purgeStale();
	}

	private static ApiException unauthorized() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Session expired, please sign in again");
	}
}
