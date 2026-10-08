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
import jakarta.persistence.Version;

@Entity
@Table(name = "product_variants")
public class ProductVariant {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id")
	private Product product;

	@Column(nullable = false)
	private String size;

	private String sku;

	@Column(nullable = false)
	private int stock;

	@Column(nullable = false)
	private int position;

	@Version
	private long version;

	protected ProductVariant() {}

	public ProductVariant(Product product, String size, int position) {
		this.product = product;
		this.size = size;
		this.position = position;
	}

	public Long getId() { return id; }
	public Product getProduct() { return product; }
	public String getSize() { return size; }
	public String getSku() { return sku; }
	public void setSku(String sku) { this.sku = sku == null || sku.isBlank() ? null : sku.trim(); }
	public int getStock() { return stock; }
	public void setStock(int stock) { this.stock = Math.max(0, stock); }
	public int getPosition() { return position; }
	public void setPosition(int position) { this.position = position; }
}
