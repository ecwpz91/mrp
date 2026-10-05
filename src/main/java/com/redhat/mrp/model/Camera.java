package com.redhat.mrp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Camera {

	private String id;
	private String name;

	@JsonProperty("full_name")
	private String fullName;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	@Override
	public String toString() {
		return "{id=" + id + ",name=" + name + ",fullName=" + fullName + "}";
	}

}
