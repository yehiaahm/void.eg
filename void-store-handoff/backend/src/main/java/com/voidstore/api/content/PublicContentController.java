package com.voidstore.api.content;

import com.voidstore.api.catalog.CatalogDtos;
import com.voidstore.api.catalog.CatalogDtos.DropDto;
import com.voidstore.api.catalog.Drop;
import com.voidstore.api.catalog.DropRepository;
import com.voidstore.api.common.ApiException;
import com.voidstore.api.common.Localized;
import com.voidstore.api.shipping.ShippingZone;
import com.voidstore.api.shipping.ShippingZoneRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PublicContentController {

	private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic();

	public record StoreSettingsDto(DropDto currentDrop, DropDto nextDrop, Localized shippingReturns,
			String instagramUrl, String tiktokUrl, String contactEmail, String contactPhone, boolean codEnabled, int returnWindowDays,
			boolean storeClosed, Localized closedMessage) {}

	public record PageDto(String slug, Localized title, Localized body, Instant updatedAt) {}

	public record ZoneDto(String code, Localized name, int fee, String etaDays) {}

	private final SettingsService settings;
	private final DropRepository drops;
	private final PageRepository pages;
	private final ShippingZoneRepository zones;

	public PublicContentController(SettingsService settings, DropRepository drops, PageRepository pages,
			ShippingZoneRepository zones) {
		this.settings = settings;
		this.drops = drops;
		this.pages = pages;
		this.zones = zones;
	}

	@GetMapping("/settings")
	@Transactional(readOnly = true)
	public ResponseEntity<StoreSettingsDto> storeSettings() {
		var current = drops.findFirstByStatusOrderByNumberDesc(Drop.Status.LIVE)
				.or(() -> drops.findAllByOrderByNumberDesc().stream().findFirst())
				.orElse(null);
		var next = drops.findFirstByStatusOrderByNumberAsc(Drop.Status.UPCOMING).orElse(null);
		var s = settings.all();
		var body = new StoreSettingsDto(
				CatalogDtos.drop(current), CatalogDtos.drop(next),
				Localized.of(s.get("shipping_returns_en"), s.get("shipping_returns_ar")),
				blankToNull(s.get("instagram_url")), blankToNull(s.get("tiktok_url")),
				blankToNull(s.get("contact_email")), blankToNull(s.get("contact_phone")),
				Boolean.parseBoolean(s.getOrDefault("cod_enabled", "true")),
				settings.integer("return_window_days", 7),
				Boolean.parseBoolean(s.getOrDefault("store_closed", "false")),
				Localized.of(s.get("closed_message_en"), s.get("closed_message_ar")));
		return ResponseEntity.ok().cacheControl(CACHE).body(body);
	}

	@GetMapping("/pages/{slug}")
	@Transactional(readOnly = true)
	public ResponseEntity<PageDto> page(@PathVariable String slug) {
		var p = pages.findBySlug(slug).orElseThrow(() -> ApiException.notFound("Page"));
		return ResponseEntity.ok().cacheControl(CACHE).body(new PageDto(p.getSlug(),
				Localized.of(p.getTitleEn(), p.getTitleAr()), Localized.of(p.getBodyEn(), p.getBodyAr()), p.getUpdatedAt()));
	}

	@GetMapping("/shipping-zones")
	@Transactional(readOnly = true)
	public List<ZoneDto> shippingZones() {
		return zones.findAllByOrderBySortOrderAsc().stream()
				.filter(ShippingZone::isAvailable)
				.map(z -> new ZoneDto(z.getCode(), Localized.of(z.getNameEn(), z.getNameAr()), z.getFee(), z.getEtaDays()))
				.toList();
	}

	private static String blankToNull(String v) {
		return v == null || v.isBlank() ? null : v;
	}
}
