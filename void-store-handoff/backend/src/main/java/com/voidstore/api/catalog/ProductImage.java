package com.voidstore.api.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_images")
public class ProductImage {

	/** front / back / detail / body — matches the gallery slots in the design. */
	public static final java.util.Set<String> KINDS = java.util.Set.of("front", "back", "detail", "body");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id")
	private Product product;

	@Column(nullable = false)
	private String url;

	@Column(nullable = false)
	private String kind;

	@Column(nullable = false)
	private int position;

	@Column(name = "alt_en", nullable = false) private String altEn = "";
	@Column(name = "alt_ar", nullable = false) private String altAr = "";

	protected ProductImage() {}

	public ProductImage(Product product, String url, String kind, int position) {
		this.product = product;
		this.url = url;
		this.kind = kind;
		this.position = position;
	}

	public Long getId() { return id; }
	public Product getProduct() { return product; }
	public String getUrl() { return url; }
	public String getKind() { return kind; }
	public void setKind(String kind) { this.kind = kind; }
	public int getPosition() { return position; }
	public void setPosition(int position) { this.position = position; }
	public String getAltEn() { return altEn; }
	public void setAltEn(String v) { this.altEn = v == null ? "" : v; }
	public String getAltAr() { return altAr; }
	public void setAltAr(String v) { this.altAr = v == null ? "" : v; }
}
