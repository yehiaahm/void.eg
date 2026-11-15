package com.voidstore.api.order;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Component;

/**
 * Row locks for order mutations (PV-1). Every code path that can put stock back (cancel, return, auto-cancel)
 * calls {@link #lock} first, so two concurrent requests for the same order run one after the other and the second
 * one sees the committed state of the first instead of its own stale copy.
 *
 * <p>The refresh uses {@code SELECT … FOR UPDATE} (a current read), so it returns the latest committed values even
 * inside a REPEATABLE READ transaction that has already read the order.
 */
@Component
public class OrderLocks {

	private final EntityManager em;

	public OrderLocks(EntityManager em) {
		this.em = em;
	}

	/** Locks the order and its lines and reloads them from the database. Must run inside a transaction. */
	public Order lock(Order o) {
		em.refresh(o, LockModeType.PESSIMISTIC_WRITE);
		for (var item : o.getItems()) em.refresh(item, LockModeType.PESSIMISTIC_WRITE);
		return o;
	}
}
