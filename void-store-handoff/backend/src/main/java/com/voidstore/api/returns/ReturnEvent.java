package com.voidstore.api.returns;

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

@Entity
@Table(name = "return_events")
public class ReturnEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id")
	private ReturnRequest request;

	@Enumerated(EnumType.STRING)
	@Column(name = "from_status")
	private ReturnRequest.Status fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "to_status", nullable = false)
	private ReturnRequest.Status toStatus;

	@Column(nullable = false)
	private String note = "";

	@Column(name = "actor_name", nullable = false)
	private String actorName = "";

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	protected ReturnEvent() {}

	public ReturnEvent(ReturnRequest request, ReturnRequest.Status from, ReturnRequest.Status to, String note, String actorName) {
		this.request = request;
		this.fromStatus = from;
		this.toStatus = to;
		this.note = note == null ? "" : note;
		this.actorName = actorName == null ? "" : actorName;
	}

	public ReturnRequest.Status getFromStatus() { return fromStatus; }
	public ReturnRequest.Status getToStatus() { return toStatus; }
	public String getNote() { return note; }
	public String getActorName() { return actorName; }
	public Instant getCreatedAt() { return createdAt; }
}
