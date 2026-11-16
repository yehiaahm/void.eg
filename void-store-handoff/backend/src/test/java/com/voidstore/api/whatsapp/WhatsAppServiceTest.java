package com.voidstore.api.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.voidstore.api.config.WhatsAppProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

/** WhatsApp order updates: off unless fully configured, and a request body Meta accepts. */
class WhatsAppServiceTest {

	private static WhatsAppService service(boolean enabled, String token, String phoneId, String version) {
		return new WhatsAppService(new WhatsAppProperties(enabled, token, phoneId, version, "void_order_"));
	}

	@Test
	void isOnlyActiveWhenSwitchedOnAndFullyConfigured() {
		assertThat(service(true, "tok", "123456789012345", "v21.0").isActive()).isTrue();
		assertThat(service(false, "tok", "123456789012345", "v21.0").isActive()).isFalse(); // the default install
		assertThat(service(true, "", "123456789012345", "v21.0").isActive()).isFalse();
		assertThat(service(true, null, "123456789012345", "v21.0").isActive()).isFalse();
		assertThat(service(true, "tok", "", "v21.0").isActive()).isFalse();
		assertThat(service(true, "tok", "12/../x", "v21.0").isActive()).isFalse(); // the id ends up in a URL
		assertThat(service(true, "tok", "123456789012345", "21").isActive()).isFalse();
	}

	@Test
	void templateNamesUseThePrefix() {
		assertThat(service(true, "t", "12345", "v21.0").templateName("shipped")).isEqualTo("void_order_shipped");
		var custom = new WhatsAppService(new WhatsAppProperties(true, "t", "12345", "v21.0", " shop_ "));
		assertThat(custom.templateName("placed")).isEqualTo("shop_placed");
		var blank = new WhatsAppService(new WhatsAppProperties(true, "t", "12345", "v21.0", ""));
		assertThat(blank.templateName("placed")).isEqualTo("void_order_placed");
	}

	@Test
	void payloadIsATemplateMessageWithBodyVariables() {
		String p = WhatsAppService.payload("201001234567", "void_order_shipped", "ar", List.of("منى", "VOID-001042", "https://x.test/ar/track?order=VOID-001042&t=abc"));
		assertThat(p).isEqualTo("{\"messaging_product\":\"whatsapp\",\"to\":\"201001234567\",\"type\":\"template\",\"template\":{\"name\":\"void_order_shipped\","
				+ "\"language\":{\"code\":\"ar\"},\"components\":[{\"type\":\"body\",\"parameters\":["
				+ "{\"type\":\"text\",\"text\":\"منى\"},{\"type\":\"text\",\"text\":\"VOID-001042\"},"
				+ "{\"type\":\"text\",\"text\":\"https://x.test/ar/track?order=VOID-001042&t=abc\"}]}]}}");
	}

	@Test
	void variablesAreEscapedAndCleanedForWhatsApp() {
		// a quote or backslash in a customer's name must not break the JSON, and line breaks are not allowed in variables
		String p = WhatsAppService.payload("201001234567", "t", "en", List.of("Mo \"The\" \\ Man\n\tX", "  ", "ok"));
		assertThat(p).contains("\"text\":\"Mo \\\"The\\\" \\\\ Man X\"").contains("\"text\":\"-\"").doesNotContain("\n");
		assertThat(WhatsAppService.variable("a".repeat(2000))).hasSize(1000);
		assertThat(WhatsAppService.variable(null)).isEqualTo("-");
	}
}
