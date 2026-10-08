package com.voidstore.api.catalog;

import com.voidstore.api.catalog.CatalogDtos.ProductDto;
import com.voidstore.api.common.ApiException;
import java.time.Duration;
import java.util.List;
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
		var list = products.findAllByStatusOrdered(Product.Status.ACTIVE).stream().map(CatalogDtos::product).toList();
		return ResponseEntity.ok().cacheControl(CACHE).body(list);
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
