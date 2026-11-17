package com.voidstore.api.shipping;

import com.voidstore.api.auth.RateLimiter;
import com.voidstore.api.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** "Use my location" at checkout: {@code GET /api/v1/geocode/reverse?lat=…&lng=…&lang=ar|en} → the address at that point. */
@RestController
@RequestMapping("/api/v1/geocode")
public class GeocodeController {

	static final int PER_IP = 12; // per 5 minutes: a few retries, never a scraper
	static final int GLOBAL_PER_MINUTE = 40; // the public Nominatim server asks for at most ~1 request/second

	private final ReverseGeocoder geocoder;
	private final RateLimiter limiter;

	public GeocodeController(ReverseGeocoder geocoder, RateLimiter limiter) {
		this.geocoder = geocoder;
		this.limiter = limiter;
	}

	@GetMapping("/reverse")
	public ReverseGeocoder.Place reverse(@RequestParam(required = false) String lat, @RequestParam(required = false) String lng,
			@RequestParam(required = false, defaultValue = "en") String lang, HttpServletRequest http) {
		limiter.check("geocode-ip:" + limiter.ip(http), PER_IP, Duration.ofMinutes(5));
		limiter.check("geocode-all:global", GLOBAL_PER_MINUTE, Duration.ofMinutes(1));
		return geocoder.reverse(number(lat), number(lng), lang);
	}

	private static double number(String s) {
		try {
			if (s == null || s.isBlank() || s.length() > 24) throw new NumberFormatException();
			return Double.parseDouble(s.trim());
		} catch (NumberFormatException e) {
			throw ApiException.badRequest("invalid_coordinates", "Invalid coordinates");
		}
	}
}
