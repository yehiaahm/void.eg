package com.voidstore.api.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DropRepository extends JpaRepository<Drop, Long> {
	List<Drop> findAllByOrderByNumberDesc();
	Optional<Drop> findFirstByStatusOrderByNumberDesc(Drop.Status status);
	Optional<Drop> findFirstByStatusOrderByNumberAsc(Drop.Status status);
	boolean existsByNumber(int number);
}
