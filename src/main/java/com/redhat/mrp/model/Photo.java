package com.redhat.mrp.model;

import java.net.URL;

public class Photo {

	private long id;
	private long sol;
	private Camera camera;
	private URL imgSrc;
	private String earthDate;
	private Rover rover;
	private String title;
	private String caption;

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public long getSol() {
		return sol;
	}

	public void setSol(long sol) {
		this.sol = sol;
	}

	public Camera getCamera() {
		return camera;
	}

	public void setCamera(Camera camera) {
		this.camera = camera;
	}

	public URL getImgSrc() {
		return imgSrc;
	}

	public void setImgSrc(URL imgSrc) {
		this.imgSrc = imgSrc;
	}

	public String getEarthDate() {
		return earthDate;
	}

	public void setEarthDate(String earthDate) {
		this.earthDate = earthDate;
	}

	public Rover getRover() {
		return rover;
	}

	public void setRover(Rover rover) {
		this.rover = rover;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getCaption() {
		return caption;
	}

	public void setCaption(String caption) {
		this.caption = caption;
	}

	@Override
	public String toString() {
		return "{id=" + id + ", sol=" + sol + ", camera=" + camera + ", imgSrc=" + imgSrc + ", earthDate=" + earthDate
				+ ", rover=" + rover + "}";
	}

}
