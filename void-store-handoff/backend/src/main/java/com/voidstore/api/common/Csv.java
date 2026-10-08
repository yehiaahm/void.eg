package com.voidstore.api.common;

/** Helpers for the admin's CSV exports. */
public final class Csv {
	private Csv() {}

	/** Byte-order mark so Excel opens Arabic correctly. */
	public static final String BOM = "﻿";

	/** Quote for CSV, and neutralise spreadsheet formula injection. */
	public static String csv(String v) {
		if (v == null) return "";
		String s = v;
		if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
		return "\"" + s.replace("\"", "\"\"") + "\"";
	}
}
