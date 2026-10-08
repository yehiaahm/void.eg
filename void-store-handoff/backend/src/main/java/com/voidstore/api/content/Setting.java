package com.voidstore.api.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "settings")
public class Setting {

	@Id
	@Column(name = "k")
	private String key;

	@Column(name = "v", nullable = false, columnDefinition = "TEXT")
	private String value;

	protected Setting() {}

	public Setting(String key, String value) {
		this.key = key;
		this.value = value;
	}

	public String getKey() { return key; }
	public String getValue() { return value; }
	public void setValue(String value) { this.value = value; }
}
