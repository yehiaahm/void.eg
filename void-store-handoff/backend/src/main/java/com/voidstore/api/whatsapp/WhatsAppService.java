package com.voidstore.api.whatsapp;

import com.voidstore.api.config.WhatsAppProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends approved WhatsApp template messages (Meta Cloud API) in the background. Like {@code MailService}, a failure
 * is logged and never reaches the shopper, and nothing is sent unless the owner switched it on and configured it.
 */
@Service
public class WhatsAppService {

	private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);

	private final WhatsAppProperties props;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	public WhatsAppService(WhatsAppProperties props) {
		this.props = props;
	}

	/** True only when enabled and fully configured; the storefront shows the opt-in checkbox only then. */
	public boolean isActive() {
		return props.enabled() && notBlank(props.token()) && props.phoneNumberId() != null && props.phoneNumberId().matches("\\d{5,30}")
				&& props.apiVersion() != null && props.apiVersion().matches("v\\d{1,3}\\.\\d{1,2}");
	}

	/** Approved template name for an order step: {@code <prefix>placed}, {@code <prefix>shipped}, … */
	public String templateName(String step) {
		String prefix = notBlank(props.templatePrefix()) ? props.templatePrefix().trim() : "void_order_";
		return prefix + step;
	}

	/**
	 * @param toInternational digits only, country code first (201001234567)
	 * @param language        template language code the owner approved: {@code ar} or {@code en}
	 * @param params          body variables {{1}}, {{2}}, … in order
	 */
	@Async
	public void sendTemplate(String toInternational, String template, String language, List<String> params) {
		if (!isActive() || toInternational == null || !toInternational.matches("\\d{8,15}")) return;
		String tail = toInternational.substring(toInternational.length() - 4);
		try {
			var req = HttpRequest.newBuilder(URI.create("https://graph.facebook.com/" + props.apiVersion() + "/" + props.phoneNumberId() + "/messages"))
					.timeout(Duration.ofSeconds(10))
					.header("Authorization", "Bearer " + props.token())
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(payload(toInternational, template, language, params), StandardCharsets.UTF_8))
					.build();
			var res = http.send(req, HttpResponse.BodyHandlers.ofString());
			if (res.statusCode() >= 300) {
				String body = res.body() == null ? "" : res.body();
				log.warn("WhatsApp {} to ***{} failed: HTTP {} {}", template, tail, res.statusCode(), body.length() > 300 ? body.substring(0, 300) : body);
			}
		} catch (Exception e) {
			log.warn("WhatsApp {} to ***{} failed: {}", template, tail, e.toString());
		}
	}

	/** The Cloud API request body for a template message with text variables in the body. */
	static String payload(String to, String template, String language, List<String> params) {
		var sb = new StringBuilder("{\"messaging_product\":\"whatsapp\",\"to\":\"").append(json(to))
				.append("\",\"type\":\"template\",\"template\":{\"name\":\"").append(json(template))
				.append("\",\"language\":{\"code\":\"").append(json(language)).append("\"}");
		if (!params.isEmpty()) {
			sb.append(",\"components\":[{\"type\":\"body\",\"parameters\":[");
			for (int i = 0; i < params.size(); i++) {
				if (i > 0) sb.append(',');
				sb.append("{\"type\":\"text\",\"text\":\"").append(json(variable(params.get(i)))).append("\"}");
			}
			sb.append("]}]");
		}
		return sb.append("}}").toString();
	}

	/** WhatsApp rejects variables with line breaks, tabs or long runs of spaces, and caps their length. */
	static String variable(String s) {
		String v = s == null ? "" : s.replaceAll("\\s+", " ").trim();
		if (v.isEmpty()) v = "-";
		return v.length() > 1000 ? v.substring(0, 1000) : v;
	}

	private static String json(String s) {
		var sb = new StringBuilder();
		for (char c : s.toCharArray()) {
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				default -> {
					if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
					else sb.append(c);
				}
			}
		}
		return sb.toString();
	}

	private static boolean notBlank(String s) {
		return s != null && !s.isBlank();
	}
}
