package com.voidstore.api.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WishlistRepository extends JpaRepository<WishlistItem, Long> {

	@Query("select w from WishlistItem w join fetch w.product where w.user.id = :userId order by w.createdAt desc")
	List<WishlistItem> findAllForUser(Long userId);

	@Query("select w from WishlistItem w where w.user.id = :userId and w.product.slug = :slug")
	Optional<WishlistItem> findOne(Long userId, String slug);

	interface SaveCount {
		Long getProductId();
		Long getSaves();
	}

	/** product id → how many customers saved it (admin insight). */
	@Query("select w.product.id as productId, count(w) as saves from WishlistItem w group by w.product.id")
	List<SaveCount> countsByProduct();
}
