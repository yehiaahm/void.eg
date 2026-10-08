package com.voidstore.api.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {
	@Query("select p from Product p left join fetch p.drop where p.status = :status order by p.sortOrder, p.id")
	List<Product> findAllByStatusOrdered(Product.Status status);

	@Query("select p from Product p left join fetch p.drop where p.slug = :slug")
	Optional<Product> findBySlug(String slug);

	@Query("select p from Product p left join fetch p.drop order by p.sortOrder, p.id")
	List<Product> findAllOrdered();

	boolean existsBySlug(String slug);
}
