package com.voidstore.api.content;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PageRepository extends JpaRepository<Page, Long> {
	Optional<Page> findBySlug(String slug);
	List<Page> findAllByOrderByIdAsc();
}
