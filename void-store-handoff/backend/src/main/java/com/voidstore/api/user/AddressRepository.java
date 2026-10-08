package com.voidstore.api.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {
	List<Address> findAllByUserIdOrderByIsDefaultDescIdAsc(Long userId);
	Optional<Address> findByIdAndUserId(Long id, Long userId);
}
