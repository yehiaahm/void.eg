package com.voidstore.api.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {
	Optional<User> findByEmail(String email);
	boolean existsByEmail(String email);
	boolean existsByRole(User.Role role);
	List<User> findAllByRoleInOrderByCreatedAtAsc(Collection<User.Role> roles);

	@Query("""
			select u from User u where u.role = com.voidstore.api.user.User.Role.CUSTOMER
			and (:q is null or lower(u.email) like lower(concat('%', :q, '%'))
			     or lower(u.name) like lower(concat('%', :q, '%')) or u.phone like concat('%', :q, '%'))
			""")
	Page<User> searchCustomers(String q, Pageable pageable);
}
