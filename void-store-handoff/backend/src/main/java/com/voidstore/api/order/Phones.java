package com.voidstore.api.order;

import com.voidstore.api.common.ApiException;
import java.util.regex.Pattern;

/** Egyptian mobile numbers, normalised to the local 11-digit form (01XXXXXXXXX). */
public final class Phones {
	private static final Pattern EG_MOBILE = Pattern.compile("01[0125]\\d{8}");

	private Phones() {}

	public static String normalize(String raw) {
		if (raw == null) return "";
		String d = raw.replaceAll("[\\s\\-()]", "");
		// Arabic-Indic digits → ASCII
		StringBuilder sb = new StringBuilder();
		for (char c : d.toCharArray()) {
			if (c >= '٠' && c <= '٩') sb.append((char) ('0' + (c - '٠')));
			else if (c >= '۰' && c <= '۹') sb.append((char) ('0' + (c - '۰')));
			else sb.append(c);
		}
		d = sb.toString();
		if (d.startsWith("+20")) d = "0" + d.substring(3);
		else if (d.startsWith("0020")) d = "0" + d.substring(4);
		else if (d.startsWith("20") && d.length() == 12) d = "0" + d.substring(2);
		return d;
	}

	public static String requireValid(String raw) {
		String p = normalize(raw);
		if (!EG_MOBILE.matcher(p).matches()) {
			throw ApiException.badRequest("invalid_phone", "Enter a valid Egyptian mobile number");
		}
		return p;
	}
}
