package com.voidstore.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.voidstore.api.common.ApiException;
import com.voidstore.api.config.AppProperties;
import com.voidstore.api.mail.MailService;
import com.voidstore.api.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** VOID-06: the audit trail exists, carries who/where/what, and cannot be forged or leak secrets. */
class AuditLogTest {

	private final ClientIpResolver ips = new ClientIpResolver(ClientIpResolver.DEFAULT_TRUSTED);
	private final AuditLog audit = new AuditLog(ips);
	private ListAppender<ILoggingEvent> out;
	private Logger logger;

	@BeforeEach
	void attach() {
		logger = (Logger) LoggerFactory.getLogger(AuditLog.LOGGER_NAME);
		out = new ListAppender<>();
		out.start();
		logger.addAppender(out);
	}

	@AfterEach
	void detach() {
		logger.detachAppender(out);
		SecurityContextHolder.clearContext();
		RequestContextHolder.resetRequestAttributes();
	}

	private String last() {
		return out.list.get(out.list.size() - 1).getFormattedMessage();
	}

	@Test
	void writesAStructuredLineAtInfoOrWarn() {
		audit.info("staff.created", "target", 7, "role", "ADMIN");
		assertThat(out.list.get(0).getLevel()).isEqualTo(Level.INFO);
		assertThat(last()).startsWith("event=staff.created actor=anonymous").contains("target=\"7\"").contains("role=\"ADMIN\"");
		audit.warn("auth.refresh.reuse", "user", 3);
		assertThat(out.list.get(1).getLevel()).isEqualTo(Level.WARN);
	}

	@Test
	void userControlledValuesCannotForgeLogLines() {
		audit.info("x", "name", "Eve\nevent=auth.login.success actor=1\r ");
		String line = last();
		assertThat(line).doesNotContain("\n").doesNotContain("\r").doesNotContain(" ");
		assertThat(line).contains("name=\"Eve_event=auth.login.success actor=1__\"");
		audit.info("x", "q", "a\"b\\c");
		assertThat(last()).contains("q=\"a\\\"b\\\\c\"");
	}

	@Test
	void longValuesAreTruncated() {
		audit.info("x", "v", "a".repeat(5000));
		assertThat(last().length()).isLessThan(400);
	}

	@Test
	void fingerprintIsStableCaseInsensitiveAndHidesTheAddress() {
		String f = AuditLog.fingerprint("Victim@Example.com");
		assertThat(f).hasSize(12).isEqualTo(AuditLog.fingerprint(" victim@example.com ")).doesNotContain("victim");
		assertThat(AuditLog.fingerprint("other@example.com")).isNotEqualTo(f);
	}

	@Test
	void recordsRequestContextAndIgnoresASpoofedForwardedHeader() {
		var req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
		req.setRemoteAddr("198.51.100.20");
		req.addHeader("X-Forwarded-For", "1.2.3.4");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
		audit.info("auth.login.success");
		assertThat(last()).contains("ip=198.51.100.20").contains("method=POST").contains("path=/api/v1/auth/login")
				.doesNotContain("1.2.3.4");
	}

	@Test
	void recordsTheAuthenticatedActorAndRole() {
		var jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("42").claim("role", "ADMIN").claim("name", "n").build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
		audit.warn("staff.updated", "target", 9);
		assertThat(last()).contains("actor=42").contains("role=ADMIN");
	}

	@Test
	void failedLoginIsAuditedWithoutThePasswordOrThePlainEmail() {
		var users = mock(UserRepository.class);
		when(users.findByEmail(anyString())).thenReturn(Optional.empty());
		var service = new AuthService(users, mock(RefreshTokenRepository.class), mock(PasswordResetTokenRepository.class),
				mock(PasswordEncoder.class), mock(JwtService.class), mock(MailService.class),
				new AppProperties("http://x", "u", "m", new AppProperties.Jwt("s", 15, 30, false), null), audit);
		try {
			service.login("Victim@Example.com", "hunter2-super-secret");
		} catch (ApiException expected) {
			assertThat(expected.getCode()).isEqualTo("bad_credentials");
		}
		String line = last();
		assertThat(line).contains("event=auth.login.failed").contains(AuditLog.fingerprint("victim@example.com"));
		assertThat(line).doesNotContain("hunter2").doesNotContain("ictim@").doesNotContain("Example.com");
		assertThat(out.list.get(0).getLevel()).isEqualTo(Level.WARN);
	}
}
