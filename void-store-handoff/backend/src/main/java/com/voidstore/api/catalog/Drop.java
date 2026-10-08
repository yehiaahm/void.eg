package com.voidstore.api.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "drops")
public class Drop {

	public enum Status { UPCOMING, LIVE, CLOSED }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private int number;

	@Column(name = "name_en", nullable = false)
	private String nameEn;

	@Column(name = "name_ar", nullable = false)
	private String nameAr;

	@Column(name = "starts_at")
	private Instant startsAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status = Status.UPCOMING;

	public Long getId() { return id; }
	public int getNumber() { return number; }
	public void setNumber(int number) { this.number = number; }
	public String getNameEn() { return nameEn; }
	public void setNameEn(String nameEn) { this.nameEn = nameEn; }
	public String getNameAr() { return nameAr; }
	public void setNameAr(String nameAr) { this.nameAr = nameAr; }
	public Instant getStartsAt() { return startsAt; }
	public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
	public Status getStatus() { return status; }
	public void setStatus(Status status) { this.status = status; }
}
