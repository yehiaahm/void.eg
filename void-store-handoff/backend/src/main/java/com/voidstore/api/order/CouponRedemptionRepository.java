package com.voidstore.api.order;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {
	long countByCouponIdAndEmail(Long couponId, String email);

	Optional<CouponRedemption> findByOrderId(Long orderId);
}
