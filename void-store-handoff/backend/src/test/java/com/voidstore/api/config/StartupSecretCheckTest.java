package com.voidstore.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * VOID-01: the real SpringApplication start-up (with the listener from META-INF/spring.factories) must abort for a
 * missing, short or placeholder secret, before any bean (and therefore any database) is created.
 */
class StartupSecretCheckTest {

	@Configuration
	static class Empty {}

	private static ConfigurableApplicationContext start(String secretArg) {
		var app = new SpringApplication(Empty.class);
		app.setWebApplicationType(WebApplicationType.NONE);
		app.setLogStartupInfo(false);
		return app.run("--spring.main.banner-mode=off", secretArg);
	}

	@Test
	void startupFailsWhenTheSecretIsMissing() {
		assertThatThrownBy(() -> start("--void.jwt.secret=")).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("JWT_SECRET");
	}

	@Test
	void startupFailsForTheFormerPublicDefault() {
		assertThatThrownBy(() -> start("--void.jwt.secret=dev-only-secret-change-me-0123456789abcdef0123456789"))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("placeholder");
	}

	@Test
	void startupFailsForAShortSecretAndNeverEchoesIt() {
		assertThatThrownBy(() -> start("--void.jwt.secret=hunter2-short")).isInstanceOf(IllegalStateException.class)
				.hasMessageNotContaining("hunter2");
	}

	@Test
	void startupSucceedsWithAStrongRandomSecret() {
		try (var ctx = start("--void.jwt.secret=" + JwtSecretPolicyTest.strong())) {
			assertThat(ctx.isActive()).isTrue();
		}
	}
}
