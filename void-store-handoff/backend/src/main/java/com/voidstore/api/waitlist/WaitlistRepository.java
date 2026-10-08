package com.voidstore.api.waitlist;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

	long countByContactedFalse();

	/** A second sign-up with the same number updates the open entry instead of adding a duplicate. */
	Optional<WaitlistEntry> findFirstByPhoneAndContactedFalseOrderByIdDesc(String phone);

	@Query("""
			select w from WaitlistEntry w
			where (:contacted is null or w.contacted = :contacted)
			  and (:q is null or lower(w.name) like lower(concat('%', :q, '%')) or w.phone like concat('%', :q, '%')
			       or lower(w.email) like lower(concat('%', :q, '%')))
			""")
	Page<WaitlistEntry> search(Boolean contacted, String q, Pageable pageable);
}
