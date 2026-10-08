package com.voidstore.api.catalog;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "products")
public class Product {

	public enum Status { DRAFT, ACTIVE, ARCHIVED }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String slug;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "drop_id")
	private Drop drop;

	@Column(name = "category_en", nullable = false) private String categoryEn = "";
	@Column(name = "category_ar", nullable = false) private String categoryAr = "";
	@Column(name = "name_en", nullable = false) private String nameEn;
	@Column(name = "name_ar", nullable = false) private String nameAr;
	@Column(name = "description_en", nullable = false, columnDefinition = "TEXT") private String descriptionEn = "";
	@Column(name = "description_ar", nullable = false, columnDefinition = "TEXT") private String descriptionAr = "";

	/** Piastres; null = not priced yet (cannot be ordered). */
	private Integer price;
	@Column(name = "compare_at_price") private Integer compareAtPrice;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status = Status.DRAFT;

	@Column(name = "fit_en", nullable = false) private String fitEn = "";
	@Column(name = "fit_ar", nullable = false) private String fitAr = "";
	@Column(name = "fabric_en", nullable = false) private String fabricEn = "";
	@Column(name = "fabric_ar", nullable = false) private String fabricAr = "";
	@Column(name = "weight_en", nullable = false) private String weightEn = "";
	@Column(name = "weight_ar", nullable = false) private String weightAr = "";
	@Column(name = "colour_en", nullable = false) private String colourEn = "";
	@Column(name = "colour_ar", nullable = false) private String colourAr = "";
	@Column(name = "care_en", nullable = false) private String careEn = "";
	@Column(name = "care_ar", nullable = false) private String careAr = "";
	@Column(name = "size_fit_en", nullable = false, columnDefinition = "TEXT") private String sizeFitEn = "";
	@Column(name = "size_fit_ar", nullable = false, columnDefinition = "TEXT") private String sizeFitAr = "";

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	@OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position ASC")
	@BatchSize(size = 50)
	private List<ProductVariant> variants = new ArrayList<>();

	@OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position ASC")
	@BatchSize(size = 50)
	private List<ProductImage> images = new ArrayList<>();

	public boolean isPurchasable() {
		return status == Status.ACTIVE && price != null;
	}

	public Long getId() { return id; }
	public String getSlug() { return slug; }
	public void setSlug(String slug) { this.slug = slug; }
	public Drop getDrop() { return drop; }
	public void setDrop(Drop drop) { this.drop = drop; }
	public String getCategoryEn() { return categoryEn; }
	public void setCategoryEn(String v) { this.categoryEn = v; }
	public String getCategoryAr() { return categoryAr; }
	public void setCategoryAr(String v) { this.categoryAr = v; }
	public String getNameEn() { return nameEn; }
	public void setNameEn(String v) { this.nameEn = v; }
	public String getNameAr() { return nameAr; }
	public void setNameAr(String v) { this.nameAr = v; }
	public String getDescriptionEn() { return descriptionEn; }
	public void setDescriptionEn(String v) { this.descriptionEn = v; }
	public String getDescriptionAr() { return descriptionAr; }
	public void setDescriptionAr(String v) { this.descriptionAr = v; }
	public Integer getPrice() { return price; }
	public void setPrice(Integer price) { this.price = price; }
	public Integer getCompareAtPrice() { return compareAtPrice; }
	public void setCompareAtPrice(Integer v) { this.compareAtPrice = v; }
	public Status getStatus() { return status; }
	public void setStatus(Status status) { this.status = status; }
	public String getFitEn() { return fitEn; }
	public void setFitEn(String v) { this.fitEn = v; }
	public String getFitAr() { return fitAr; }
	public void setFitAr(String v) { this.fitAr = v; }
	public String getFabricEn() { return fabricEn; }
	public void setFabricEn(String v) { this.fabricEn = v; }
	public String getFabricAr() { return fabricAr; }
	public void setFabricAr(String v) { this.fabricAr = v; }
	public String getWeightEn() { return weightEn; }
	public void setWeightEn(String v) { this.weightEn = v; }
	public String getWeightAr() { return weightAr; }
	public void setWeightAr(String v) { this.weightAr = v; }
	public String getColourEn() { return colourEn; }
	public void setColourEn(String v) { this.colourEn = v; }
	public String getColourAr() { return colourAr; }
	public void setColourAr(String v) { this.colourAr = v; }
	public String getCareEn() { return careEn; }
	public void setCareEn(String v) { this.careEn = v; }
	public String getCareAr() { return careAr; }
	public void setCareAr(String v) { this.careAr = v; }
	public String getSizeFitEn() { return sizeFitEn; }
	public void setSizeFitEn(String v) { this.sizeFitEn = v; }
	public String getSizeFitAr() { return sizeFitAr; }
	public void setSizeFitAr(String v) { this.sizeFitAr = v; }
	public int getSortOrder() { return sortOrder; }
	public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
	public Instant getCreatedAt() { return createdAt; }
	public List<ProductVariant> getVariants() { return variants; }
	public List<ProductImage> getImages() { return images; }
}
