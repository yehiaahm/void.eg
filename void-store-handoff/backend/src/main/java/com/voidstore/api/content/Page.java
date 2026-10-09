package com.voidstore.api.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Policy / info page (shipping, returns, privacy, contact, about), edited in the admin. */
@Entity
@Table(name = "pages")
public class Page {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String slug;

	@Column(name = "title_en", nullable = false) private String titleEn;
	@Column(name = "title_ar", nullable = false) private String titleAr;
	@Column(name = "body_en", nullable = false, columnDefinition = "MEDIUMTEXT") private String bodyEn;
	@Column(name = "body_ar", nullable = false, columnDefinition = "MEDIUMTEXT") private String bodyAr;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private Instant updatedAt;

	public Long getId() { return id; }
	public String getSlug() { return slug; }
	public String getTitleEn() { return titleEn; }
	public void setTitleEn(String v) { this.titleEn = v; }
	public String getTitleAr() { return titleAr; }
	public void setTitleAr(String v) { this.titleAr = v; }
	public String getBodyEn() { return bodyEn; }
	public void setBodyEn(String v) { this.bodyEn = v; }
	public String getBodyAr() { return bodyAr; }
	public void setBodyAr(String v) { this.bodyAr = v; }
	public Instant getUpdatedAt() { return updatedAt; }
}
