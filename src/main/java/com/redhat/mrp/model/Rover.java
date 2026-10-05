package com.redhat.mrp.model;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Rover {

	/** Rover slug from Mars Vista v2 (e.g. curiosity). */
	private String id;
	private String name;

	@JsonProperty("landing_date")
	private String landingDate;

	@JsonProperty("launch_date")
	private String launchDate;

	private String status;

	@JsonProperty("max_sol")
	private int maxSol;

	@JsonProperty("max_date")
	private String maxDate;

	@JsonProperty("total_photos")
	private int totalPhotos;

	private Camera[] cameras;

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

	public String getLandingDate() {
		return landingDate;
	}

	public void setLandingDate(String landingDate) {
		this.landingDate = landingDate;
	}

	public String getLaunchDate() {
		return launchDate;
	}

	public void setLaunchDate(String launchDate) {
		this.launchDate = launchDate;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public int getMaxSol() {
		return maxSol;
	}

	public void setMaxSol(int maxSol) {
		this.maxSol = maxSol;
	}

	public String getMaxDate() {
		return maxDate;
	}

	public void setMaxDate(String maxDate) {
		this.maxDate = maxDate;
	}

	public int getTotalPhotos() {
		return totalPhotos;
	}

	public void setTotalPhotos(int totalPhotos) {
		this.totalPhotos = totalPhotos;
	}

	public Camera[] getCameras() {
		return cameras;
	}

	public void setCameras(Camera[] cameras) {
		this.cameras = cameras;
	}

	@Override
	public String toString() {
		return "{id=" + id + ",name=" + name + ",landingDate=" + landingDate + ",launchDate=" + launchDate + ",status="
				+ status + ",maxSol=" + maxSol + ",totalPhotos=" + totalPhotos + ",cameras=" + Arrays.toString(cameras)
				+ "}";
	}

}
