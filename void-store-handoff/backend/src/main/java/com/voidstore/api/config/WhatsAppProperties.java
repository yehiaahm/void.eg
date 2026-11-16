package com.voidstore.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WhatsApp order updates through Meta's WhatsApp Business Cloud API (`void.whatsapp.*`). Off unless
 * {@code enabled} is true AND a token and phone-number id are set, so a fresh install never tries to message anyone.
 *
 * @param templatePrefix approved template names are {@code <prefix>placed / confirmed / shipped / delivered / cancelled}
 *                       (each created in Meta Business Manager in both {@code ar} and {@code en})
 */
@ConfigurationProperties("void.whatsapp")
public record WhatsAppProperties(boolean enabled, String token, String phoneNumberId, String apiVersion, String templatePrefix) {}
