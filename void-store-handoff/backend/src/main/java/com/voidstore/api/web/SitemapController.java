package com.voidstore.api.web;

import com.voidstore.api.catalog.Product;
import com.voidstore.api.catalog.ProductRepository;
import com.voidstore.api.config.AppProperties;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/** /sitemap.xml — home, products and policy pages in both languages, with hreflang alternates. */
@RestController
public class SitemapController {

	private final ProductRepository products;
	private final AppProperties props;

	public SitemapController(ProductRepository products, AppProperties props) {
		this.products = products;
		this.props = props;
	}

	@GetMapping(value = {"/sitemap.xml", "/api/v1/sitemap.xml"}, produces = MediaType.APPLICATION_XML_VALUE)
	@Transactional(readOnly = true)
	public ResponseEntity<String> sitemap() {
		String o = props.frontendUrl().split(",")[0].trim().replaceAll("/$", "");
		var paths = new java.util.ArrayList<>(List.of("", "/pages/shipping", "/pages/returns", "/pages/privacy", "/pages/contact"));
		products.findAllByStatusOrdered(Product.Status.ACTIVE).forEach(p -> paths.add("/product/" + p.getSlug()));
		var sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\" xmlns:xhtml=\"http://www.w3.org/1999/xhtml\">\n");
		for (String p : paths) {
			for (String lang : List.of("en", "ar")) {
				sb.append("  <url><loc>").append(x(o + "/" + lang + p)).append("</loc>")
						.append("<xhtml:link rel=\"alternate\" hreflang=\"en\" href=\"").append(x(o + "/en" + p)).append("\"/>")
						.append("<xhtml:link rel=\"alternate\" hreflang=\"ar\" href=\"").append(x(o + "/ar" + p)).append("\"/>")
						.append("</url>\n");
			}
		}
		sb.append("</urlset>\n");
		return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic()).body(sb.toString());
	}

	@GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> robots() {
		String o = props.frontendUrl().split(",")[0].trim().replaceAll("/$", "");
		String body = String.join("\n", "User-agent: *", "Disallow: /admin", "Disallow: /api/",
				"Disallow: /en/account", "Disallow: /ar/account", "Disallow: /en/checkout", "Disallow: /ar/checkout",
				"Sitemap: " + o + "/sitemap.xml", "");
		return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(6)).cachePublic()).body(body);
	}

	private static String x(String s) {
		return HtmlUtils.htmlEscape(s);
	}
}
