package com.voidstore.api.returns;

import com.voidstore.api.order.Order;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A customer's request to return (refund) or exchange pieces of a delivered order.
 * REQUESTED → APPROVED → RECEIVED → COMPLETED, or REQUESTED → REJECTED / CANCELLED.
 */
@Entity
@Table(name = "return_requests")
public class ReturnRequest {

	public enum Type { RETURN, EXCHANGE }

	public enum Reason {
		SIZE, WRONG_ITEM, DEFECT, NOT_AS_DESCRIBED, CHANGED_MIND, OTHER;

		/** The store's mistake: only these qualify for a refund, and their exchanges ship free. */
		public boolean ourMistake() {
			return this == WRONG_ITEM || this == DEFECT || this == NOT_AS_DESCRIBED;
		}
	}

	public enum Status {
		REQUESTED, APPROVED, REJECTED, RECEIVED, COMPLETED, CANCELLED;

		private static final Map<Status, Set<Status>> NEXT = Map.of(
				REQUESTED, EnumSet.of(APPROVED, REJECTED, CANCELLED),
				APPROVED, EnumSet.of(RECEIVED),
				RECEIVED, EnumSet.of(COMPLETED),
				REJECTED, EnumSet.noneOf(Status.class),
				COMPLETED, EnumSet.noneOf(Status.class),
				CANCELLED, EnumSet.noneOf(Status.class));

		public boolean canMoveTo(Status s) {
			return NEXT.get(this).contains(s);
		}

		public Set<Status> next() {
			return NEXT.get(this);
		}

		/** Still holds a claim on the order's pieces. */
		public boolean isOpen() {
			return this == REQUESTED || this == APPROVED || this == RECEIVED;
		}
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String number;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id")
	private Order order;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Type type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status = Status.REQUESTED;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Reason reason;

	@Column(name = "customer_note", nullable = false)
	private String customerNote = "";

	/** Piastres, set when a RETURN is completed. */
	@Column(name = "refund_amount")
	private Integer refundAmount;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", insertable = false, updatable = false)
	private Instant updatedAt;

	@OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id ASC")
	private List<ReturnItem> items = new ArrayList<>();

	@OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id ASC")
	private List<ReturnEvent> events = new ArrayList<>();

	protected ReturnRequest() {}

	public ReturnRequest(Order order, Type type, Reason reason, String note) {
		this.order = order;
		this.type = type;
		this.reason = reason;
		this.customerNote = note == null ? "" : note.trim();
	}

	/** Value of the returned pieces at the price paid. */
	public int itemsValue() {
		return items.stream().mapToInt(i -> i.getOrderItem().getUnitPrice() * i.getQty()).sum();
	}

	public Long getId() { return id; }
	public String getNumber() { return number; }
	public void setNumber(String number) { this.number = number; }
	public Order getOrder() { return order; }
	public Type getType() { return type; }
	public Status getStatus() { return status; }
	public void setStatus(Status status) { this.status = status; }
	public Reason getReason() { return reason; }
	public String getCustomerNote() { return customerNote; }
	public Integer getRefundAmount() { return refundAmount; }
	public void setRefundAmount(Integer v) { this.refundAmount = v; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public List<ReturnItem> getItems() { return items; }
	public List<ReturnEvent> getEvents() { return events; }
}
