package com.voidstore.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under the `void.*` prefix in application.yml. */
@ConfigurationProperties("void")
public record AppProperties(
		String frontendUrl,
		String uploadsDir,
		String mailFrom,
		Jwt jwt,
		Bootstrap bootstrap) {

	public record Jwt(String secret, long accessMinutes, long refreshDays, boolean secureCookie) {}

	/** First owner account, created on startup when no OWNER exists yet. */
	public record Bootstrap(String ownerEmail, String ownerPassword, String ownerName) {}
}
