package com.voidstore.api.returns;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReturnRepository extends JpaRepository<ReturnRequest, Long> {

	Optional<ReturnRequest> findByNumber(String number);

	List<ReturnRequest> findAllByOrderIdOrderByCreatedAtDesc(Long orderId);

	long countByStatus(ReturnRequest.Status status);

	@Query("""
			select r from ReturnRequest r join fetch r.order o
			where (:status is null or r.status = :status)
			  and (:q is null or r.number like concat('%', :q, '%') or o.number like concat('%', :q, '%')
			       or lower(o.name) like lower(concat('%', :q, '%')) or o.phone like concat('%', :q, '%'))
			""")
	Page<ReturnRequest> search(ReturnRequest.Status status, String q, Pageable pageable);
}
