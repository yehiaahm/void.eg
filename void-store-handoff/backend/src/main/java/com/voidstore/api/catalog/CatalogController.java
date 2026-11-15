package com.voidstore.api.catalog;

import com.voidstore.api.catalog.CatalogDtos.ProductDto;
import com.voidstore.api.common.ApiException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class CatalogController {

	private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic();

	private final ProductRepository products;

	public CatalogController(ProductRepository products) {
		this.products = products;
	}

	@GetMapping
	@Transactional(readOnly = true)
	public ResponseEntity<List<ProductDto>> list() {
		// Clothes first, caps after (owner request, 2026-11-15). Stable sort: inside each group the admin's
		// sort order is kept.
		var list = products.findAllByStatusOrdered(Product.Status.ACTIVE).stream()
				.sorted(Comparator.comparing(CatalogController::isCap))
				.map(CatalogDtos::product).toList();
		return ResponseEntity.ok().cacheControl(CACHE).body(list);
	}

	private static final Pattern CAP_EN = Pattern.compile("\\bcaps?\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern CAP_AR = Pattern.compile("(^|\\s)(كاب|كابات|كابس)(\\s|$)");

	/** A cap is recognised by its category, name or slug (never the description, which mentions caps worn). */
	static boolean isCap(Product p) {
		String en = p.getCategoryEn() + " " + p.getNameEn() + " " + p.getSlug().replace('-', ' ');
		String ar = p.getCategoryAr() + " " + p.getNameAr();
		return CAP_EN.matcher(en).find() || CAP_AR.matcher(ar).find();
	}

	@GetMapping("/{slug}")
	@Transactional(readOnly = true)
	public ResponseEntity<ProductDto> get(@PathVariable String slug) {
		var p = products.findBySlug(slug)
				.filter(x -> x.getStatus() == Product.Status.ACTIVE)
				.orElseThrow(() -> ApiException.notFound("Product"));
		return ResponseEntity.ok().cacheControl(CACHE).body(CatalogDtos.product(p));
	}
}
