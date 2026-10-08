package com.voidstore.api.order;

import com.voidstore.api.user.User;
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
import jakarta.persistence.Table;
import java.time.Instant;

/** Audit trail: placement, status changes, notes, payment updates — and who did them. */
@Entity
@Table(name = "order_events")
public class OrderEvent {

	public enum Type { PLACED, STATUS, NOTE, PAYMENT }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id")
	private Order order;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Type type;

	@Enumerated(EnumType.STRING)
	@Column(name = "from_status")
	private Order.Status fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "to_status")
	private Order.Status toStatus;

	@Column(nullable = false)
	private String note = "";

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "actor_id")
	private User actor;

	@Column(name = "actor_name", nullable = false)
	private String actorName = "";

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	protected OrderEvent() {}

	public OrderEvent(Order order, Type type, Order.Status from, Order.Status to, String note, User actor) {
		this.order = order;
		this.type = type;
		this.fromStatus = from;
		this.toStatus = to;
		this.note = note == null ? "" : note;
		this.actor = actor;
		this.actorName = actor == null ? "" : actor.getName();
	}

	public Long getId() { return id; }
	public Type getType() { return type; }
	public Order.Status getFromStatus() { return fromStatus; }
	public Order.Status getToStatus() { return toStatus; }
	public String getNote() { return note; }
	public String getActorName() { return actorName; }
	public Instant getCreatedAt() { return createdAt; }
}
