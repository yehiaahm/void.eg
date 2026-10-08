package com.voidstore.api.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long> {

	Optional<Order> findByNumber(String number);

	Page<Order> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

	@Query("""
			select o from Order o
			where (:status is null or o.status = :status)
			  and (:from is null or o.createdAt >= :from)
			  and (:to is null or o.createdAt < :to)
			  and (:q is null or o.number like concat('%', :q, '%') or lower(o.email) like lower(concat('%', :q, '%'))
			       or lower(o.name) like lower(concat('%', :q, '%')) or o.phone like concat('%', :q, '%'))
			""")
	Page<Order> search(Order.Status status, String q, Instant from, Instant to, Pageable pageable);

	@Query("select o from Order o where o.createdAt >= :from and o.createdAt < :to")
	List<Order> findCreatedBetween(Instant from, Instant to);

	long countByStatus(Order.Status status);

	@Query("select count(o) from Order o where o.user.id = :userId")
	long countByUserId(Long userId);

	@Query("select coalesce(sum(o.total), 0) from Order o where o.user.id = :userId and o.status not in (com.voidstore.api.order.Order.Status.CANCELLED, com.voidstore.api.order.Order.Status.RETURNED)")
	long totalSpentByUser(Long userId);
}
