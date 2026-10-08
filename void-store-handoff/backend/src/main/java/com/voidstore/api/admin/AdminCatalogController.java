package com.voidstore.api.admin;

import com.voidstore.api.catalog.Drop;
import com.voidstore.api.catalog.DropRepository;
import com.voidstore.api.catalog.Product;
import com.voidstore.api.catalog.ProductImage;
import com.voidstore.api.catalog.ProductRepository;
import com.voidstore.api.catalog.ProductVariant;
import com.voidstore.api.common.ApiException;
import com.voidstore.api.common.Localized;
import com.voidstore.api.user.WishlistRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminCatalogController {

	// ---------------------------------------------------------------- DTOs

	public record VariantDto(Long id, String size, String sku, int stock) {}

	public record ImageDto(long id, String url, String kind, int position, Localized alt) {}

	public record ProductDto(long id, String slug, Long dropId, String status, Localized category, Localized name,
			Localized description, Integer price, Integer compareAtPrice, Localized fit, Localized fabric, Localized weight,
			Localized colour, Localized care, Localized sizeFit, int sortOrder, List<VariantDto> variants,
			List<ImageDto> images, Instant createdAt) {}

	public record ProductRowDto(long id, String slug, String status, Localized name, Integer price, int totalStock,
			int lowSizes, String image, Integer dropNumber, int sortOrder, long saves) {}

	public record VariantReq(@NotBlank @Size(max = 8) String size, @Size(max = 64) String sku, @Min(0) @Max(100000) int stock) {}

	public record ProductReq(
			@NotBlank @Size(max = 120) @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*", message = "lowercase letters, numbers and dashes") String slug,
			Long dropId,
			@NotBlank String status,
			@NotNull Localized category, @NotNull Localized name, @NotNull Localized description,
			@Min(0) Integer price, @Min(0) Integer compareAtPrice,
			@NotNull Localized fit, @NotNull Localized fabric, @NotNull Localized weight, @NotNull Localized colour,
			@NotNull Localized care, @NotNull Localized sizeFit,
			int sortOrder,
			@NotNull @Size(min = 1, max = 12) List<@Valid VariantReq> variants) {}

	public record StockReq(@NotNull List<@Valid VariantReq> variants) {}

	public record ImageMetaReq(@NotBlank String kind, Localized alt) {}

	public record ImageOrderReq(@NotNull List<Long> ids) {}

	public record DropDto(long id, int number, Localized name, Instant startsAt, String status, long productCount) {}

	public record DropReq(@Min(1) int number, @NotNull Localized name, Instant startsAt, @NotBlank String status) {}

	// ---------------------------------------------------------------- wiring

	private final ProductRepository products;
	private final DropRepository drops;
	private final ImageStorage storage;
	private final EntityManager em;
	private final WishlistRepository wishlist;

	public AdminCatalogController(ProductRepository products, DropRepository drops, ImageStorage storage, EntityManager em,
			WishlistRepository wishlist) {
		this.wishlist = wishlist;
		this.products = products;
		this.drops = drops;
		this.storage = storage;
		this.em = em;
	}

	private static Localized loc(String en, String ar) {
		return Localized.of(en, ar);
	}

	static ProductDto dto(Product p) {
		return new ProductDto(p.getId(), p.getSlug(), p.getDrop() == null ? null : p.getDrop().getId(), p.getStatus().name(),
				loc(p.getCategoryEn(), p.getCategoryAr()), loc(p.getNameEn(), p.getNameAr()),
				loc(p.getDescriptionEn(), p.getDescriptionAr()), p.getPrice(), p.getCompareAtPrice(),
				loc(p.getFitEn(), p.getFitAr()), loc(p.getFabricEn(), p.getFabricAr()), loc(p.getWeightEn(), p.getWeightAr()),
				loc(p.getColourEn(), p.getColourAr()), loc(p.getCareEn(), p.getCareAr()), loc(p.getSizeFitEn(), p.getSizeFitAr()),
				p.getSortOrder(),
				p.getVariants().stream().map(v -> new VariantDto(v.getId(), v.getSize(), v.getSku(), v.getStock())).toList(),
				p.getImages().stream().map(i -> new ImageDto(i.getId(), i.getUrl(), i.getKind(), i.getPosition(),
						loc(i.getAltEn(), i.getAltAr()))).toList(),
				p.getCreatedAt());
	}

	private Product product(long id) {
		return products.findById(id).orElseThrow(() -> ApiException.notFound("Product"));
	}

	private static String s(Localized l, boolean ar) {
		if (l == null) return "";
		String v = ar ? l.ar() : l.en();
		return v == null ? "" : v.trim();
	}

	// ---------------------------------------------------------------- products

	@GetMapping("/products")
	@PreAuthorize(Roles.STAFF)
	@Transactional(readOnly = true)
	public List<ProductRowDto> listProducts(@RequestParam(defaultValue = "3") int lowStock) {
		var saves = new java.util.HashMap<Long, Long>();
		wishlist.countsByProduct().forEach(c -> saves.put(c.getProductId(), c.getSaves()));
		return products.findAllOrdered().stream().map(p -> new ProductRowDto(p.getId(), p.getSlug(), p.getStatus().name(),
				loc(p.getNameEn(), p.getNameAr()), p.getPrice(),
				p.getVariants().stream().mapToInt(ProductVariant::getStock).sum(),
				(int) p.getVariants().stream().filter(v -> v.getStock() <= lowStock).count(),
				p.getImages().stream().filter(i -> "front".equals(i.getKind())).map(ProductImage::getUrl).findFirst()
						.orElse(p.getImages().isEmpty() ? null : p.getImages().getFirst().getUrl()),
				p.getDrop() == null ? null : p.getDrop().getNumber(), p.getSortOrder(), saves.getOrDefault(p.getId(), 0L))).toList();
	}

	@GetMapping("/products/{id}")
	@PreAuthorize(Roles.STAFF)
	@Transactional(readOnly = true)
	public ProductDto getProduct(@PathVariable long id) {
		return dto(product(id));
	}

	@PostMapping("/products")
	@PreAuthorize(Roles.ADMIN)
	@Transactional
	public ProductDto createProduct(@Valid @RequestBody ProductReq req) {
		if (products.existsBySlug(req.slug())) throw ApiException.conflict("slug_taken", "Another product already uses this URL");
		var p = new Product();
		apply(p, req);
		products.saveAndFlush(p);
		return dto(p);
	}

	@PutMapping("/products/{id}")
	@PreAuthorize(Roles.ADMIN)
	@Transactional
	public ProductDto updateProduct(@PathVariable long id, @Valid @RequestBody ProductReq req) {
		var p = product(id);
		if (!p.getSlug().equals(req.slug()) && products.existsBySlug(req.slug())) {
			throw ApiException.conflict("slug_taken", "Another product already uses this URL");
		}
		apply(p, req);
		products.flush();
		return dto(p);
	}

	private void apply(Product p, ProductReq r) {
		Product.Status status;
		try {
			status = Product.Status.valueOf(r.status());
		} catch (IllegalArgumentException e) {
			throw ApiException.badRequest("bad_status", "Unknown status");
		}
		if (status == Product.Status.ACTIVE && r.price() == null) {
			throw ApiException.badRequest("price_required", "Set a price before publishing");
		}
		if (s(r.name(), false).isEmpty()) throw ApiException.badRequest("name_required", "English name is required");
		p.setSlug(r.slug());
		p.setDrop(r.dropId() == null ? null : drops.findById(r.dropId()).orElseThrow(() -> ApiException.notFound("Drop")));
		p.setStatus(status);
		p.setCategoryEn(s(r.category(), false));
		p.setCategoryAr(s(r.category(), true));
		p.setNameEn(s(r.name(), false));
		p.setNameAr(s(r.name(), true).isEmpty() ? s(r.name(), false) : s(r.name(), true));
		p.setDescriptionEn(s(r.description(), false));
		p.setDescriptionAr(s(r.description(), true));
		p.setPrice(r.price());
		p.setCompareAtPrice(r.compareAtPrice());
		p.setFitEn(s(r.fit(), false));
		p.setFitAr(s(r.fit(), true));
		p.setFabricEn(s(r.fabric(), false));
		p.setFabricAr(s(r.fabric(), true));
		p.setWeightEn(s(r.weight(), false));
		p.setWeightAr(s(r.weight(), true));
		p.setColourEn(s(r.colour(), false));
		p.setColourAr(s(r.colour(), true));
		p.setCareEn(s(r.care(), false));
		p.setCareAr(s(r.care(), true));
		p.setSizeFitEn(s(r.sizeFit(), false));
		p.setSizeFitAr(s(r.sizeFit(), true));
		p.setSortOrder(r.sortOrder());
		syncVariants(p, r.variants(), true);
	}

	/** Upsert variants by size; with {@code removeMissing}, sizes not in the list are deleted. */
	private void syncVariants(Product p, List<VariantReq> reqs, boolean removeMissing) {
		Set<String> seen = new HashSet<>();
		int pos = 0;
		for (var r : reqs) {
			String size = r.size().trim().toUpperCase();
			if (!seen.add(size)) throw ApiException.badRequest("duplicate_size", "Size " + size + " is listed twice");
			var v = p.getVariants().stream().filter(x -> x.getSize().equals(size)).findFirst().orElse(null);
			if (v == null) {
				if (!removeMissing) throw ApiException.badRequest("unknown_size", "Unknown size " + size);
				v = new ProductVariant(p, size, pos);
				p.getVariants().add(v);
			}
			v.setSku(r.sku());
			v.setStock(r.stock());
			v.setPosition(pos++);
		}
		if (removeMissing) p.getVariants().removeIf(v -> !seen.contains(v.getSize()));
	}

	/** Quick stock edit — allowed for STAFF as well. */
	@PutMapping("/products/{id}/stock")
	@PreAuthorize(Roles.STAFF)
	@Transactional
	public ProductDto updateStock(@PathVariable long id, @Valid @RequestBody StockReq req) {
		var p = product(id);
		syncVariants(p, req.variants(), false);
		return dto(p);
	}

	/** Archive when the product has orders (keeps history), otherwise delete for good. */
	@DeleteMapping("/products/{id}")
	@PreAuthorize(Roles.ADMIN)
	@Transactional
	public ResponseEntity<Void> deleteProduct(@PathVariable long id) {
		var p = product(id);
		long ordered = em.createQuery("select count(i) from OrderItem i where i.product.id = :id", Long.class)
				.setParameter("id", id).getSingleResult();
		if (ordered > 0) {
			p.setStatus(Product.Status.ARCHIVED);
		} else {
			var urls = p.getImages().stream().map(ProductImage::getUrl).toList();
			products.delete(p);
			products.flush();
			urls.forEach(this::deleteFileIfUnused);
		}
		return ResponseEntity.noContent().build();
	}

	// ---------------------------------------------------------------- images

	@PostMapping(path = "/products/{id}/images", consumes = "multipart/form-data")
	@PreAuthorize(Roles.ADMIN)
	@Transactional
	public ProductDto uploadImage(@PathVariable long id, @RequestParam("file") MultipartFile file,
			@RequestParam(defaultValue = "front") String kind) {
		if (!ProductImage.KINDS.contains(kind)) throw ApiException.badRequest("bad_kind", "Unknown image slot");
		var p = product(id);
		if (p.getImages().size() >= 12) throw ApiException.badRequest("too_many", "12 images per product max");
		String url = storage.store(file);
		p.getImages().add(new ProductImage(p, url, kind, p.getImages().size()));
		products.flush();
		return dto(p);
	}

	@PutMapping("/products/{id}/images/{imageId}")
	@PreAuthorize(Roles.ADMIN)
	@Transactional
	public ProductDto updateImage(@PathVariable long id, @PathVariable long imageId, @Valid @RequestBody ImageMetaReq req) {
		if (!ProductImage.KINDS.contains(req.kind())) throw ApiException.badRequest("bad_kind", "Unknown image slot");
		var p = product(id);
		var img = p.getImages().stream().filter(i -> i.getId() == imageId).findFirst()
				.orElseThrow(() -> ApiException.notFound("Image"));
		img.setKind(req.kind());
		img.setAltEn(s(req.alt(), false));
		img.setAltAr(s(req.alt(), true));
		return dto(p);
	}

	@PutMapping("/products/{id}/images/order")
	@PreAuthorize(Roles.ADMIN)
	@Transactional
	public ProductDto reorderImages(@PathVariable long id, @Valid @RequestBody ImageOrderReq req) {
		var p = product(id);
		for (int i = 0; i < req.ids().size(); i++) {
			long imgId = req.ids().get(i);
			int pos = i;
			p.getImages().stream().filter(x -> x.getId() == imgId).findFirst().ifPresent(x -> x.setPosition(pos));
		}
		p.getImages().sort((a, b) -> Integer.compare(a.getPosition(), b.getPosition()));
		return dto(p);
	}

	@DeleteMapping("/products/{id}/images/{imageId}")
	@PreAuthorize(Roles.ADMIN)
	@Transactional
	public ProductDto deleteImage(@PathVariable long id, @PathVariable long imageId) {
		var p = product(id);
		var img = p.getImages().stream().filter(i -> i.getId() == imageId).findFirst()
				.orElseThrow(() -> ApiException.notFound("Image"));
		String url = img.getUrl();
		p.getImages().remove(img);
		products.flush();
		deleteFileIfUnused(url);
		return dto(p);
	}

	private void deleteFileIfUnused(String url) {
		long refs = em.createQuery("select count(i) from ProductImage i where i.url = :u", Long.class)
				.setParameter("u", url).getSingleResult();
		if (refs == 0) storage.delete(url);
	}

	// ---------------------------------------------------------------- drops

	@GetMapping("/drops")
	@PreAuthorize(Roles.STAFF)
	@Transactional(readOnly = true)
	public List<DropDto> listDrops() {
		return drops.findAllByOrderByNumberDesc().stream().map(this::dropDto).toList();
	}

	private DropDto dropDto(Drop d) {
		long count = em.createQuery("select count(p) from Product p where p.drop.id = :id", Long.class)
				.setParameter("id", d.getId()).getSingleResult();
		return new DropDto(d.getId(), d.getNumber(), loc(d.getNameEn(), d.getNameAr()), d.getStartsAt(), d.getStatus().name(), count);
	}

	@PostMapping("/drops")
	@PreAuthorize(Roles.ADMIN)
	@Transactional
	public DropDto createDrop(@Valid @RequestBody DropReq req) {
		if (drops.existsByNumber(req.number())) throw ApiException.conflict("number_taken", "Drop number already exists");
		var d = new Drop();
		applyDrop(d, req);
		drops.save(d);
		return dropDto(d);
	}

	@PutMapping("/drops/{id}")
	@PreAuthorize(Roles.ADMIN)
	@Transactional
	public DropDto updateDrop(@PathVariable long id, @Valid @RequestBody DropReq req) {
		var d = drops.findById(id).orElseThrow(() -> ApiException.notFound("Drop"));
		if (d.getNumber() != req.number() && drops.existsByNumber(req.number())) {
			throw ApiException.conflict("number_taken", "Drop number already exists");
		}
		applyDrop(d, req);
		return dropDto(d);
	}

	@DeleteMapping("/drops/{id}")
	@PreAuthorize(Roles.ADMIN)
	@Transactional
	public ResponseEntity<Void> deleteDrop(@PathVariable long id) {
		var d = drops.findById(id).orElseThrow(() -> ApiException.notFound("Drop"));
		drops.delete(d); // products keep existing; their drop_id is set to NULL by the FK
		return ResponseEntity.noContent().build();
	}

	private void applyDrop(Drop d, DropReq r) {
		try {
			d.setStatus(Drop.Status.valueOf(r.status()));
		} catch (IllegalArgumentException e) {
			throw ApiException.badRequest("bad_status", "Unknown status");
		}
		if (s(r.name(), false).isEmpty()) throw ApiException.badRequest("name_required", "English name is required");
		d.setNumber(r.number());
		d.setNameEn(s(r.name(), false));
		d.setNameAr(s(r.name(), true));
		d.setStartsAt(r.startsAt());
	}
}
