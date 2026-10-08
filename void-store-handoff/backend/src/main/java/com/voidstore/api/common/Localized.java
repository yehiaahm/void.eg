package com.voidstore.api.common;

/** A text value in English and Arabic. */
public record Localized(String en, String ar) {

	public static Localized of(String en, String ar) {
		return new Localized(en == null ? "" : en, ar == null ? "" : ar);
	}

	public String get(String lang) {
		return "ar".equals(lang) && ar != null && !ar.isBlank() ? ar : en;
	}
}
