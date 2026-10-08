package com.voidstore.api.contact;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {

	long countByHandledFalse();

	@Query("""
			select m from ContactMessage m
			where (:handled is null or m.handled = :handled)
			  and (:q is null or lower(m.name) like lower(concat('%', :q, '%')) or lower(m.email) like lower(concat('%', :q, '%'))
			       or m.phone like concat('%', :q, '%') or lower(m.message) like lower(concat('%', :q, '%')))
			""")
	Page<ContactMessage> search(Boolean handled, String q, Pageable pageable);
}
