package com.voidstore.api.content;

import com.voidstore.api.common.ApiException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Key/value store settings editable from the admin (Content → Settings). */
@Service
public class SettingsService {

	/** Keys the admin may edit. Anything else is rejected. */
	public static final Set<String> EDITABLE = Set.of(
			"shipping_returns_en", "shipping_returns_ar", "instagram_url", "tiktok_url",
			"contact_email", "contact_phone", "cod_enabled", "low_stock_threshold", "return_window_days",
			"store_closed", "closed_message_en", "closed_message_ar");

	private final SettingRepository repo;

	public SettingsService(SettingRepository repo) {
		this.repo = repo;
	}

	@Transactional(readOnly = true)
	public Map<String, String> all() {
		Map<String, String> m = new TreeMap<>();
		repo.findAll().forEach(s -> m.put(s.getKey(), s.getValue()));
		return m;
	}

	@Transactional(readOnly = true)
	public String get(String key, String fallback) {
		return repo.findById(key).map(Setting::getValue).filter(v -> !v.isBlank()).orElse(fallback);
	}

	public boolean bool(String key, boolean fallback) {
		return Boolean.parseBoolean(get(key, String.valueOf(fallback)));
	}

	/** The owner's "close the store" switch: shoppers see the waitlist page and can't place orders. */
	public boolean storeClosed() {
		return bool("store_closed", false);
	}

	public int integer(String key, int fallback) {
		try {
			return Integer.parseInt(get(key, String.valueOf(fallback)).trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	@Transactional
	public void putAll(Map<String, String> values) {
		values.forEach((k, v) -> {
			if (!EDITABLE.contains(k)) throw ApiException.badRequest("unknown_setting", "Unknown setting " + k);
			var s = repo.findById(k).orElseGet(() -> new Setting(k, ""));
			s.setValue(v == null ? "" : v.trim());
			repo.save(s);
		});
	}
}
