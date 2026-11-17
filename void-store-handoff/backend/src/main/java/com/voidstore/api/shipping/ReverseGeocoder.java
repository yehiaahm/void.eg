package com.voidstore.api.shipping;

import com.voidstore.api.common.ApiException;
import com.voidstore.api.config.AppProperties;
import com.voidstore.api.config.GeocodingProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Turns the shopper's coordinates into an address for checkout ("Use my location"), through OpenStreetMap Nominatim.
 * Coordinates are never stored or logged; results are cached for an hour per ~10 m cell so a retry (or a busy launch
 * minute) does not hit the public server again. Points outside Egypt are answered without calling anyone.
 */
@Service
public class ReverseGeocoder {

	private static final Logger log = LoggerFactory.getLogger(ReverseGeocoder.class);

	/** What the checkout form can fill. Governorate is the raw name from the map; the storefront matches it to a delivery zone. */
	public record Place(boolean inEgypt, String governorate, String city, String street, String building) {
		static Place outsideEgypt() {
			return new Place(false, "", "", "", "");
		}

		static Place empty() {
			return new Place(true, "", "", "", "");
		}

		boolean isEmpty() {
			return governorate.isEmpty() && city.isEmpty() && street.isEmpty() && building.isEmpty();
		}
	}

	/** Seam for tests: GET the URI and return the JSON object. */
	@FunctionalInterface
	interface Fetcher {
		Map<String, Object> get(URI uri);
	}

	// Rough bounding box of Egypt (a little generous); the real check is the country code in the answer.
	static final double MIN_LAT = 21.5, MAX_LAT = 32.0, MIN_LNG = 24.5, MAX_LNG = 37.2;
	static final int CITY_MAX = 120, STREET_MAX = 255, BUILDING_MAX = 120;
	private static final String DEFAULT_URL = "https://nominatim.openstreetmap.org";
	private static final Duration TTL = Duration.ofHours(1);
	private static final int CACHE_MAX = 2000;

	private record Cached(Place place, long at) {}

	private final GeocodingProperties props;
	private final Fetcher fetcher;
	private final Map<String, Cached> cache = new LinkedHashMap<>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
			return size() > CACHE_MAX;
		}
	};

	@Autowired
	public ReverseGeocoder(GeocodingProperties props, AppProperties app) {
		this(props, restFetcher(userAgent(app)));
	}

	ReverseGeocoder(GeocodingProperties props, Fetcher fetcher) {
		this.props = props;
		this.fetcher = fetcher;
	}

	public boolean isEnabled() {
		return props.enabled();
	}

	/** @param lang {@code ar} or {@code en} (anything else is English) */
	public Place reverse(double lat, double lng, String lang) {
		if (!validCoordinates(lat, lng)) throw ApiException.badRequest("invalid_coordinates", "Invalid coordinates");
		if (lat < MIN_LAT || lat > MAX_LAT || lng < MIN_LNG || lng > MAX_LNG) return Place.outsideEgypt();
		if (!props.enabled()) throw unavailable();

		String language = "ar".equals(lang) ? "ar" : "en";
		String key = String.format(Locale.ROOT, "%.4f,%.4f,%s", lat, lng, language);
		synchronized (cache) {
			var hit = cache.get(key);
			if (hit != null && System.currentTimeMillis() - hit.at < TTL.toMillis()) return hit.place;
		}

		Place place;
		try {
			String base = props.url() == null || props.url().isBlank() ? DEFAULT_URL : props.url().trim().replaceAll("/+$", "");
			var uri = URI.create(String.format(Locale.ROOT,
					"%s/reverse?format=jsonv2&lat=%.6f&lon=%.6f&zoom=18&addressdetails=1&accept-language=%s", base, lat, lng, language));
			place = parse(fetchWithOneRetry(uri));
		} catch (RuntimeException e) {
			// the exception text can contain the request URL (and so the coordinates): log the type only
			log.warn("Reverse geocoding failed: {}{}", e.getClass().getSimpleName(),
					e.getCause() == null ? "" : " <- " + e.getCause().getClass().getName());
			throw unavailable();
		}
		synchronized (cache) {
			cache.put(key, new Cached(place, System.currentTimeMillis()));
		}
		return place;
	}

	/** A connection hiccup to the public server is common enough to try once more; HTTP error answers are not retried. */
	private Map<String, Object> fetchWithOneRetry(URI uri) {
		try {
			return fetcher.get(uri);
		} catch (ResourceAccessException e) {
			return fetcher.get(uri);
		}
	}

	/** Finite coordinates inside the (generous) bounding box of Egypt. */
	public static boolean inEgypt(double lat, double lng) {
		return validCoordinates(lat, lng) && lat >= MIN_LAT && lat <= MAX_LAT && lng >= MIN_LNG && lng <= MAX_LNG;
	}

	static boolean validCoordinates(double lat, double lng) {
		return Double.isFinite(lat) && Double.isFinite(lng) && Math.abs(lat) <= 90 && Math.abs(lng) <= 180;
	}

	/** Nominatim {@code jsonv2} answer → the fields checkout can use. Never throws on odd data. */
	@SuppressWarnings("unchecked")
	static Place parse(Map<String, Object> json) {
		if (json == null || json.get("error") != null || !(json.get("address") instanceof Map<?, ?> raw)) return Place.empty();
		var a = (Map<String, Object>) raw;
		String country = str(a, "country_code");
		if (!country.isEmpty() && !"eg".equalsIgnoreCase(country)) return Place.outsideEgypt();

		String governorate = first(a, "state", "region");
		String city = first(a, "city", "town", "village", "municipality");
		String area = first(a, "suburb", "neighbourhood", "city_district", "quarter");
		String place = area.isEmpty() ? city : city.isEmpty() || area.equalsIgnoreCase(city) ? area : area + ", " + city;
		var p = new Place(true, cut(governorate, 80), cut(place, CITY_MAX), cut(first(a, "road", "pedestrian", "residential"), STREET_MAX),
				cut(str(a, "house_number"), BUILDING_MAX));
		return p.isEmpty() ? Place.empty() : p;
	}

	private static String first(Map<String, Object> a, String... keys) {
		for (String k : keys) {
			String v = str(a, k);
			if (!v.isEmpty()) return v;
		}
		return "";
	}

	private static String str(Map<String, Object> a, String key) {
		return a.get(key) instanceof String s ? s.replaceAll("\\s+", " ").trim() : "";
	}

	private static String cut(String s, int max) {
		return s.length() > max ? s.substring(0, max).trim() : s;
	}

	private static ApiException unavailable() {
		return new ApiException(HttpStatus.BAD_GATEWAY, "geocode_unavailable", "Address lookup is unavailable");
	}

	/** Nominatim's usage policy asks for an identifying User-Agent: the store's own address, never a customer's. */
	private static String userAgent(AppProperties app) {
		String site = app.frontendUrl() == null ? "" : app.frontendUrl().split(",")[0].trim();
		return "VOID-store/1.0" + (site.isEmpty() ? "" : " (" + site + ")");
	}

	private static Fetcher restFetcher(String userAgent) {
		var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build());
		factory.setReadTimeout(Duration.ofSeconds(5));
		var rest = RestClient.builder().requestFactory(factory).defaultHeader("User-Agent", userAgent).defaultHeader("Accept", "application/json")
				.build();
		return uri -> rest.get().uri(uri).retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
	}
}
