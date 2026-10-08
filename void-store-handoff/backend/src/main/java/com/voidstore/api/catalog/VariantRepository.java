package com.voidstore.api.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VariantRepository extends JpaRepository<ProductVariant, Long> {
	@Query("select v from ProductVariant v join fetch v.product p where p.status <> com.voidstore.api.catalog.Product.Status.ARCHIVED and v.stock <= :threshold order by v.stock, p.sortOrder")
	List<ProductVariant> findLowStock(int threshold);
}
