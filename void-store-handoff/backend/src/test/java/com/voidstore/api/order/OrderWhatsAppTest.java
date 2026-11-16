package com.voidstore.api.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.voidstore.api.config.AppProperties;
import com.voidstore.api.whatsapp.WhatsAppService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** WhatsApp updates go only to customers who opted in, only while the owner has it on, and carry the private link. */
class OrderWhatsAppTest {

	private OrderRepository orders;
	private WhatsAppService whatsapp;
	private OrderWhatsApp listener;
	private TrackingTokens tokens;
	private Order order;

	@BeforeEach
	void setUp() {
		orders = mock(OrderRepository.class);
		whatsapp = mock(WhatsAppService.class);
		when(whatsapp.isActive()).thenReturn(true);
		when(whatsapp.templateName(anyString())).thenAnswer(inv -> "void_order_" + inv.getArgument(0));
		tokens = new TrackingTokens(new SecretKeySpec("k9Vt2mQx7LpR4sYw1ZcN8bHf3JdG6aUe0TqXiOvE5yMrB".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		var props = new AppProperties("https://void.test,https://other.test", "u", "m", new AppProperties.Jwt("s", 15, 30, false), null);
		listener = new OrderWhatsApp(orders, whatsapp, props, tokens);
		order = new Order();
		order.setNumber("VOID-001042");
		order.setName("Mona Ahmed");
		order.setPhone("01001234567");
		order.setLang("ar");
		order.setWhatsappOptIn(true);
		when(orders.findById(1L)).thenReturn(Optional.of(order));
	}

	@Test
	void anOptedInCustomerGetsTheStatusTemplateWithNameNumberAndPrivateLink() {
		listener.onStatus(new OrderService.StatusChanged(1L, Order.Status.SHIPPED));
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> params = ArgumentCaptor.forClass(List.class);
		verify(whatsapp).sendTemplate(eq("201001234567"), eq("void_order_shipped"), eq("ar"), params.capture());
		assertThat(params.getValue()).containsExactly("Mona", "VOID-001042",
				"https://void.test/ar/track?order=VOID-001042&t=" + tokens.forOrder("VOID-001042"));
	}

	@Test
	void placedSendsThePlacedTemplate() {
		listener.onPlaced(new OrderService.OrderPlaced(1L, "ignored"));
		verify(whatsapp).sendTemplate(eq("201001234567"), eq("void_order_placed"), eq("ar"), anyList());
	}

	@Test
	void aCustomerWhoDidNotOptInIsNeverMessaged() {
		order.setWhatsappOptIn(false);
		listener.onStatus(new OrderService.StatusChanged(1L, Order.Status.CONFIRMED));
		listener.onPlaced(new OrderService.OrderPlaced(1L, "t"));
		verify(whatsapp, never()).sendTemplate(any(), any(), any(), any());
	}

	@Test
	void nothingHappensWhileWhatsAppIsOffOrNotConfigured() {
		when(whatsapp.isActive()).thenReturn(false);
		listener.onStatus(new OrderService.StatusChanged(1L, Order.Status.CONFIRMED));
		verify(orders, never()).findById(any());
		verify(whatsapp, never()).sendTemplate(any(), any(), any(), any());
	}

	@Test
	void statusesWithoutATemplateAndBadPhonesSendNothing() {
		listener.onStatus(new OrderService.StatusChanged(1L, Order.Status.RETURNED)); // no template for it
		order.setPhone("12345");
		listener.onStatus(new OrderService.StatusChanged(1L, Order.Status.DELIVERED)); // not an Egyptian mobile
		verify(whatsapp, never()).sendTemplate(any(), any(), any(), any());
	}

	@Test
	void unknownLanguagesFallBackToEnglish() {
		order.setLang("fr");
		listener.onStatus(new OrderService.StatusChanged(1L, Order.Status.CANCELLED));
		verify(whatsapp).sendTemplate(eq("201001234567"), eq("void_order_cancelled"), eq("en"), anyList());
	}

	@Test
	void phonesBecomeInternationalDigits() {
		assertThat(Phones.international("0100 123 4567")).isEqualTo("201001234567");
		assertThat(Phones.international("+20 100 123 4567")).isEqualTo("201001234567");
		assertThat(Phones.international("0221234567")).isNull();
		assertThat(Phones.international(null)).isNull();
	}
}
