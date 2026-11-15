package com.voidstore.api.order;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {
	/**
	 * Redemptions of this coupon by the same customer: same account, phone, delivery address or e-mail
	 * (canonical or as originally typed). Null identifiers never match.
	 */
	@Query("""
			select count(r) from CouponRedemption r
			where r.coupon.id = :couponId
			  and (r.email = :canonicalEmail or r.email = :email
			       or (:phone is not null and r.phone = :phone)
			       or (:addressKey is not null and r.addressKey = :addressKey)
			       or (:userId is not null and r.userId = :userId))
			""")
	long countForCustomer(Long couponId, String canonicalEmail, String email, String phone, String addressKey, Long userId);

	Optional<CouponRedemption> findByOrderId(Long orderId);
}
