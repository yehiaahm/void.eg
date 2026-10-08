package com.voidstore.api.mail;

import org.springframework.web.util.HtmlUtils;

/** Minimal, inline-styled email shell matching the store's black / bone palette. */
public final class EmailLayout {
	private EmailLayout() {}

	public static String esc(String s) {
		return s == null ? "" : HtmlUtils.htmlEscape(s);
	}

	public static String wrap(String lang, String bodyHtml) {
		boolean ar = "ar".equals(lang);
		String font = ar ? "'IBM Plex Sans Arabic',Tahoma,Arial,sans-serif" : "Archivo,'Helvetica Neue',Arial,sans-serif";
		return """
				<!doctype html><html lang="%s" dir="%s"><head><meta charset="utf-8"></head>
				<body style="margin:0;background:#000;color:#F2F0EB;font-family:%s">
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#000">
				<tr><td align="center" style="padding:32px 16px">
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px">
				<tr><td style="padding-bottom:28px;border-bottom:1px solid rgba(242,240,235,.15);text-align:center">
				<span dir="ltr" style="font-weight:800;font-size:18px;letter-spacing:.34em">VOID</span></td></tr>
				<tr><td style="padding:28px 0;font-size:15px;line-height:1.7;text-align:%s">%s</td></tr>
				<tr><td style="padding-top:20px;border-top:1px solid rgba(242,240,235,.15);color:#8A877F;font-size:12px;text-align:center" dir="ltr">© 2026 VOID</td></tr>
				</table></td></tr></table></body></html>
				""".formatted(ar ? "ar" : "en", ar ? "rtl" : "ltr", font, ar ? "right" : "left", bodyHtml);
	}

	public static String button(String href, String label) {
		return "<p style=\"margin:24px 0\"><a href=\"" + esc(href) + "\" style=\"display:inline-block;padding:14px 22px;"
				+ "background:#F2F0EB;color:#050506;text-decoration:none;font-size:13px;letter-spacing:.08em\">"
				+ esc(label) + "</a></p>";
	}
}
