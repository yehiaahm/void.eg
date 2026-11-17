package com.voidstore.api.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.voidstore.api.auth.AuditLog;
import com.voidstore.api.auth.ClientIpResolver;
import com.voidstore.api.auth.RateLimiter;
import com.voidstore.api.common.ApiException;
import com.voidstore.api.config.GeocodingProperties;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** "Use my location": answer shaping, Egypt-only, caching, validation and abuse limits (no network). */
class ReverseGeocoderTest {

	private static Map<String, Object> maadi() {
		var a = new HashMap<String, Object>();
		a.put("house_number", "12");
		a.put("road", "Street 9");
		a.put("suburb", "Maadi");
		a.put("city", "Cairo");
		a.put("state", "Cairo Governorate");
		a.put("country_code", "eg");
		var json = new HashMap<String, Object>();
		json.put("address", a);
		return json;
	}

	private static ReverseGeocoder geocoder(boolean enabled, ReverseGeocoder.Fetcher f) {
		return new ReverseGeocoder(new GeocodingProperties(enabled, "https://geo.test/"), f);
	}

	@Test
	void shapesAnEgyptianAddressForTheCheckoutForm() {
		var p = ReverseGeocoder.parse(maadi());
		assertThat(p.inEgypt()).isTrue();
		assertThat(p.governorate()).isEqualTo("Cairo Governorate");
		assertThat(p.city()).isEqualTo("Maadi, Cairo");
		assertThat(p.street()).isEqualTo("Street 9");
		assertThat(p.building()).isEqualTo("12");
	}

	@Test
	void doesNotRepeatTheCityWhenAreaAndCityAreTheSame() {
		var json = maadi();
		@SuppressWarnings("unchecked") var a = (Map<String, Object>) json.get("address");
		a.remove("suburb");
		assertThat(ReverseGeocoder.parse(json).city()).isEqualTo("Cairo");
		a.put("suburb", "cairo");
		assertThat(ReverseGeocoder.parse(json).city()).isEqualTo("cairo");
	}

	@Test
	void anAddressInAnotherCountryIsNotUsed() {
		var json = maadi();
		@SuppressWarnings("unchecked") var a = (Map<String, Object>) json.get("address");
		a.put("country_code", "ly");
		var p = ReverseGeocoder.parse(json);
		assertThat(p.inEgypt()).isFalse();
		assertThat(p.governorate()).isEmpty();
		assertThat(p.street()).isEmpty();
	}

	@Test
	void unknownOrBrokenAnswersGiveAnEmptyPlaceInsteadOfAnError() {
		for (Map<String, Object> bad : new Map[] {null, Map.of(), Map.of("error", "Unable to geocode"), Map.of("address", "oops"), Map.of("address", Map.of())}) {
			var p = ReverseGeocoder.parse(bad);
			assertThat(p.inEgypt()).isTrue();
			assertThat(p.governorate()).isEmpty();
			assertThat(p.city()).isEmpty();
		}
		// non-string values are ignored
		var weird = new HashMap<String, Object>();
		weird.put("address", Map.of("road", 5, "state", "Giza Governorate"));
		var p = ReverseGeocoder.parse(weird);
		assertThat(p.street()).isEmpty();
		assertThat(p.governorate()).isEqualTo("Giza Governorate");
	}

	@Test
	void valuesAreCutToTheColumnLimits() {
		var json = maadi();
		@SuppressWarnings("unchecked") var a = (Map<String, Object>) json.get("address");
		a.put("road", "x".repeat(400));
		a.put("house_number", "9".repeat(400));
		var p = ReverseGeocoder.parse(json);
		assertThat(p.street()).hasSize(ReverseGeocoder.STREET_MAX);
		assertThat(p.building()).hasSize(ReverseGeocoder.BUILDING_MAX);
	}

	@Test
	void pointsOutsideEgyptNeverReachTheMapServer() {
		var calls = new AtomicInteger();
		var g = geocoder(true, uri -> {
			calls.incrementAndGet();
			return maadi();
		});
		assertThat(g.reverse(48.8566, 2.3522, "en").inEgypt()).isFalse(); // Paris
		assertThat(g.reverse(0, 0, "en").inEgypt()).isFalse();
		assertThat(calls).hasValue(0);
	}

	@Test
	void rejectsCoordinatesThatAreNotOnEarth() {
		var g = geocoder(true, uri -> maadi());
		for (double[] c : new double[][] {{91, 30}, {-91, 30}, {30, 181}, {30, -181}, {Double.NaN, 30}, {30, Double.POSITIVE_INFINITY}}) {
			assertThatThrownBy(() -> g.reverse(c[0], c[1], "en")).isInstanceOf(ApiException.class)
					.extracting(e -> ((ApiException) e).getCode()).isEqualTo("invalid_coordinates");
		}
	}

	@Test
	void asksForTheShoppersLanguageAndCachesTheAnswer() {
		var asked = new java.util.ArrayList<URI>();
		var g = geocoder(true, uri -> {
			asked.add(uri);
			return maadi();
		});
		var first = g.reverse(29.9602, 31.2569, "ar");
		var again = g.reverse(29.96021, 31.25689, "ar"); // same ~10 m cell
		assertThat(again).isEqualTo(first);
		assertThat(asked).hasSize(1);
		assertThat(asked.get(0).toString()).startsWith("https://geo.test/reverse?").contains("accept-language=ar").contains("lat=29.960200")
				.contains("lon=31.256900");
		g.reverse(29.9602, 31.2569, "en"); // other language = other answer
		g.reverse(29.9602, 31.2569, "fr"); // unknown language falls back to English (cached with the English one)
		assertThat(asked).hasSize(2);
	}

	@Test
	void aFailingMapServerBecomesABadGatewayWithoutLeakingTheCoordinates() {
		var g = geocoder(true, uri -> {
			throw new IllegalStateException("boom " + uri);
		});
		assertThatThrownBy(() -> g.reverse(30.0444, 31.2357, "en")).isInstanceOfSatisfying(ApiException.class, e -> {
			assertThat(e.getCode()).isEqualTo("geocode_unavailable");
			assertThat(e.getStatus().value()).isEqualTo(502);
			assertThat(e.getMessage()).doesNotContain("30.04");
		});
	}

	@Test
	void retriesOnceWhenTheConnectionHiccups() {
		var calls = new AtomicInteger();
		var g = geocoder(true, uri -> {
			if (calls.incrementAndGet() == 1) throw new org.springframework.web.client.ResourceAccessException("timed out");
			return maadi();
		});
		assertThat(g.reverse(30.0444, 31.2357, "en").governorate()).isEqualTo("Cairo Governorate");
		assertThat(calls).hasValue(2);

		var down = new AtomicInteger();
		var broken = geocoder(true, uri -> {
			down.incrementAndGet();
			throw new org.springframework.web.client.ResourceAccessException("down");
		});
		assertThatThrownBy(() -> broken.reverse(30.0444, 31.2357, "en")).isInstanceOf(ApiException.class); // gives up after one retry
		assertThat(down).hasValue(2);
	}

	@Test
	void canBeSwitchedOff() {
		var g = geocoder(false, uri -> maadi());
		assertThat(g.isEnabled()).isFalse();
		assertThatThrownBy(() -> g.reverse(30.0444, 31.2357, "en")).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.getCode()).isEqualTo("geocode_unavailable"));
	}

	@Test
	void anOrderPinIsKeptOnlyWhenBothValuesAreInsideEgypt() {
		var ok = new com.voidstore.api.order.OrderDtos.AddressReq("cairo", "c", "s", null, null, 30.04441234, 31.23571234).pin();
		assertThat(ok).containsExactly(30.044412, 31.235712);
		for (Double[] bad : new Double[][] {{null, 31.2}, {30.0, null}, {null, null}, {48.85, 2.35}, {Double.NaN, 31.2}, {30.0, Double.POSITIVE_INFINITY}, {91.0, 31.2}}) {
			assertThat(new com.voidstore.api.order.OrderDtos.AddressReq("cairo", "c", "s", null, null, bad[0], bad[1]).pin()).isNull();
		}
		assertThat(new com.voidstore.api.order.OrderDtos.AddressReq("cairo", "c", "s", null, null).pin()).isNull();
	}

	// ------------------------------------------------------------------ controller

	private static GeocodeController controller(ReverseGeocoder g) {
		var ips = new ClientIpResolver(ClientIpResolver.DEFAULT_TRUSTED);
		return new GeocodeController(g, new RateLimiter(true, ips, new AuditLog(ips)));
	}

	private static MockHttpServletRequest from(String ip) {
		var r = new MockHttpServletRequest("GET", "/api/v1/geocode/reverse");
		r.setRemoteAddr(ip);
		return r;
	}

	private static String codeOf(Runnable r) {
		try {
			r.run();
			return "ok";
		} catch (ApiException e) {
			return e.getCode();
		}
	}

	@Test
	void controllerRejectsMissingOrGarbledCoordinates() {
		var c = controller(geocoder(true, uri -> maadi()));
		assertThat(codeOf(() -> c.reverse(null, "31", "en", from("198.51.100.1")))).isEqualTo("invalid_coordinates");
		assertThat(codeOf(() -> c.reverse("30", "", "en", from("198.51.100.1")))).isEqualTo("invalid_coordinates");
		assertThat(codeOf(() -> c.reverse("abc", "31", "en", from("198.51.100.1")))).isEqualTo("invalid_coordinates");
		assertThat(codeOf(() -> c.reverse("NaN", "31", "en", from("198.51.100.1")))).isEqualTo("invalid_coordinates");
		assertThat(codeOf(() -> c.reverse("1".repeat(40), "31", "en", from("198.51.100.1")))).isEqualTo("invalid_coordinates");
		assertThat(codeOf(() -> c.reverse(" 30.0444 ", "31.2357", "en", from("198.51.100.1")))).isEqualTo("ok");
	}

	@Test
	void controllerLimitsEachClient() {
		var c = controller(geocoder(true, uri -> maadi()));
		int ok = 0;
		for (int i = 0; i < GeocodeController.PER_IP + 10; i++) {
			if (codeOf(() -> c.reverse("30.0444", "31.2357", "en", from("198.51.100.7"))).equals("ok")) ok++;
		}
		assertThat(ok).isEqualTo(GeocodeController.PER_IP);
		assertThat(codeOf(() -> c.reverse("30.0444", "31.2357", "en", from("198.51.100.8")))).isEqualTo("ok"); // other client unaffected
	}
}
