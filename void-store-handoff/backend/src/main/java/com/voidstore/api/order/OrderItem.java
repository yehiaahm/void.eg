package com.voidstore.api.order;

import com.voidstore.api.catalog.Product;
import com.voidstore.api.catalog.ProductVariant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Snapshot of what was bought — survives later product edits or deletion. */
@Entity
@Table(name = "order_items")
public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id")
	private Order order;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id")
	private Product product;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "variant_id")
	private ProductVariant variant;

	@Column(nullable = false) private String slug;
	@Column(nullable = false) private String name;
	@Column(nullable = false) private String size;
	private String sku;
	@Column(name = "image_url") private String imageUrl;
	@Column(name = "unit_price", nullable = false) private int unitPrice;
	@Column(nullable = false) private int qty;
	@Column(name = "line_total", nullable = false) private int lineTotal;
	/** Pieces that came back to stock (return requests or a full return). */
	@Column(name = "returned_qty", nullable = false) private int returnedQty;

	protected OrderItem() {}

	public OrderItem(Order order, ProductVariant variant, String imageUrl, int qty) {
		var p = variant.getProduct();
		this.order = order;
		this.product = p;
		this.variant = variant;
		this.slug = p.getSlug();
		this.name = p.getNameEn();
		this.size = variant.getSize();
		this.sku = variant.getSku();
		this.imageUrl = imageUrl;
		this.unitPrice = p.getPrice();
		this.qty = qty;
		this.lineTotal = unitPrice * qty;
	}

	public Long getId() { return id; }
	public Order getOrder() { return order; }
	public Product getProduct() { return product; }
	public ProductVariant getVariant() { return variant; }
	public String getSlug() { return slug; }
	public String getName() { return name; }
	public String getSize() { return size; }
	public String getSku() { return sku; }
	public String getImageUrl() { return imageUrl; }
	public int getUnitPrice() { return unitPrice; }
	public int getQty() { return qty; }
	public int getLineTotal() { return lineTotal; }
	public int getReturnedQty() { return returnedQty; }
	public void addReturned(int n) { this.returnedQty = Math.min(qty, returnedQty + n); }
}
