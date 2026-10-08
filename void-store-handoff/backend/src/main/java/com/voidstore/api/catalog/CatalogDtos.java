package com.voidstore.api.catalog;

import com.voidstore.api.common.Localized;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Public storefront shapes — mirror frontend/src/api/types.ts. */
public final class CatalogDtos {
	private CatalogDtos() {}

	/** Storefront never reveals exact inventory above this. */
	public static final int PUBLIC_STOCK_CAP = 10;

	public record VariantDto(String size, String sku, int stock) {}

	public record ImageDto(String url, String kind, Localized alt) {}

	public record DetailsDto(Localized fit, Localized fabric, Localized weight, Localized colour, Localized care) {}

	public record ProductDto(
			long id, String slug, Long dropId, Localized category, Localized name, Localized description,
			Integer price, Integer compareAtPrice, List<VariantDto> variants, List<ImageDto> images,
			DetailsDto details, Localized sizeFit) {}

	public record DropDto(long id, int number, Localized name, Instant startsAt, String status) {}

	public static DropDto drop(Drop d) {
		if (d == null) return null;
		return new DropDto(d.getId(), d.getNumber(), Localized.of(d.getNameEn(), d.getNameAr()), d.getStartsAt(),
				d.getStatus().name().toLowerCase(Locale.ROOT));
	}

	public static ProductDto product(Product p) {
		return new ProductDto(
				p.getId(), p.getSlug(), p.getDrop() == null ? null : p.getDrop().getId(),
				Localized.of(p.getCategoryEn(), p.getCategoryAr()),
				Localized.of(p.getNameEn(), p.getNameAr()),
				Localized.of(p.getDescriptionEn(), p.getDescriptionAr()),
				p.getPrice(), p.getCompareAtPrice(),
				p.getVariants().stream()
						.map(v -> new VariantDto(v.getSize(), v.getSku(), Math.min(PUBLIC_STOCK_CAP, v.getStock())))
						.toList(),
				p.getImages().stream()
						.map(i -> new ImageDto(i.getUrl(), i.getKind(), Localized.of(i.getAltEn(), i.getAltAr())))
						.toList(),
				new DetailsDto(
						Localized.of(p.getFitEn(), p.getFitAr()),
						Localized.of(p.getFabricEn(), p.getFabricAr()),
						Localized.of(p.getWeightEn(), p.getWeightAr()),
						Localized.of(p.getColourEn(), p.getColourAr()),
						Localized.of(p.getCareEn(), p.getCareAr())),
				Localized.of(p.getSizeFitEn(), p.getSizeFitAr()));
	}
}
