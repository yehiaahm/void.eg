package com.voidstore.api.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;

/**
 * Fails application start-up (VOID-01) as soon as the environment is known, before the database, Flyway or any bean
 * is touched, when {@code void.jwt.secret} (JWT_SECRET) is missing, short, a known placeholder or low-entropy. It
 * runs after config files such as {@code local.properties} have been loaded. Registered in
 * {@code META-INF/spring.factories}. {@code SecurityConfig.jwtKey} repeats the check as defence in depth.
 */
public class JwtSecretStartupCheck implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

	@Override
	public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
		JwtSecretPolicy.validate(event.getEnvironment().getProperty("void.jwt.secret"));
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE; // after ConfigData (application.yml, local.properties, env) is loaded
	}
}
