package com.voidstore.api.order;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
	Optional<Coupon> findByCode(String code);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Coupon c where c.code = :code")
	Optional<Coupon> lockByCode(String code);

	List<Coupon> findAllByOrderByCreatedAtDesc();

	boolean existsByCode(String code);
}
