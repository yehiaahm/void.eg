package com.voidstore.api.shipping;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShippingZoneRepository extends JpaRepository<ShippingZone, Long> {
	List<ShippingZone> findAllByOrderBySortOrderAsc();
	Optional<ShippingZone> findByCode(String code);
}
