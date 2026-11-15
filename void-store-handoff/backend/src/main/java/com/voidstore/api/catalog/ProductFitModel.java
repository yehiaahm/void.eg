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

/** A model who wears the piece in the photos: height (cm) and the size they are wearing. Anonymous on purpose. */
@Entity
@Table(name = "product_fit_models")
public class ProductFitModel {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id")
	private Product product;

	@Column(name = "height_cm", nullable = false)
	private int heightCm;

	@Column(nullable = false)
	private String size;

	@Column(nullable = false)
	private int position;

	protected ProductFitModel() {}

	public Long getId() { return id; }
	public Product getProduct() { return product; }
	public int getHeightCm() { return heightCm; }
	public String getSize() { return size; }
	public int getPosition() { return position; }
}
