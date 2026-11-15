package com.voidstore.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Security audit trail (VOID-06): one line per security-relevant event on the dedicated {@code AUDIT} logger, as
 * {@code event=… actor=… role=… ip=… path=… key="value" …}. Ship the logger's output to your log platform and alert
 * on {@code auth.login.failed} bursts, {@code auth.refresh.reuse}, {@code staff.*}, {@code export.*} and
 * {@code rate_limit.exceeded}.
 *
 * <p>Rules: never pass passwords, tokens or secrets; identify people by user id or by {@link #fingerprint(String)}
 * of their e-mail, not by the raw address; every value is sanitised so user input cannot forge log lines.
 */
@Component
public class AuditLog {

	static final String LOGGER_NAME = "AUDIT";
	/** Exports with at least this many rows are logged at WARN as {@code export.*.large}. */
	public static final int LARGE_EXPORT_ROWS = 200;
	private static final Logger AUDIT = LoggerFactory.getLogger(LOGGER_NAME);
	private static final int MAX_VALUE = 200;

	private final ClientIpResolver ips;

	public AuditLog(ClientIpResolver ips) {
		this.ips = ips;
	}

	/** Normal event. {@code kv} is a flat list of key, value, key, value… */
	public void info(String event, Object... kv) {
		AUDIT.info(line(event, kv));
	}

	/** Suspicious or failed event worth alerting on. */
	public void warn(String event, Object... kv) {
		AUDIT.warn(line(event, kv));
	}

	String line(String event, Object... kv) {
		var sb = new StringBuilder("event=").append(clean(event));
		var actor = CurrentUser.get();
		sb.append(" actor=").append(actor.map(p -> String.valueOf(p.id())).orElse("anonymous"));
		actor.ifPresent(p -> sb.append(" role=").append(p.role()));
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) {
			HttpServletRequest r = a.getRequest();
			sb.append(" ip=").append(clean(ips.resolve(r)));
			sb.append(" method=").append(clean(r.getMethod())).append(" path=").append(clean(r.getRequestURI()));
		}
		for (int i = 0; i + 1 < kv.length; i += 2) {
			sb.append(' ').append(clean(String.valueOf(kv[i]))).append("=\"").append(clean(String.valueOf(kv[i + 1]))).append('"');
		}
		return sb.toString();
	}

	/** Stable, non-reversible handle for an e-mail address (first 12 hex chars of SHA-256 of the lower-cased value). */
	public static String fingerprint(String value) {
		if (value == null) return "none";
		try {
			var md = MessageDigest.getInstance("SHA-256");
			String hex = HexFormat.of().formatHex(md.digest(value.trim().toLowerCase().getBytes(StandardCharsets.UTF_8)));
			return hex.substring(0, 12);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Strips control characters (log forging), escapes quotes and backslashes, caps the length. */
	static String clean(String s) {
		if (s == null) return "";
		var sb = new StringBuilder(Math.min(s.length(), MAX_VALUE));
		for (int i = 0; i < s.length() && sb.length() < MAX_VALUE; i++) {
			char c = s.charAt(i);
			if (c < 0x20 || c == 0x7f || c == 0x2028 || c == 0x2029) sb.append('_');
			else if (c == '"' || c == '\\') sb.append('\\').append(c);
			else sb.append(c);
		}
		return sb.toString();
	}
}
