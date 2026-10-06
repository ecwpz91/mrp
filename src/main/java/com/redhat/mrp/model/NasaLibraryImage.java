package com.redhat.mrp.model;

/**
 * Curated still from NASA Image and Video Library (images-api.nasa.gov).
 */
public class NasaLibraryImage {

	private String nasaId;
	private String title;
	private String caption;
	private String credit;
	private String imageUrl;
	private String detailsUrl;

	public String getNasaId() {
		return nasaId;
	}

	public void setNasaId(String nasaId) {
		this.nasaId = nasaId;
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

	public String getCredit() {
		return credit;
	}

	public void setCredit(String credit) {
		this.credit = credit;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public void setImageUrl(String imageUrl) {
		this.imageUrl = imageUrl;
	}

	public String getDetailsUrl() {
		return detailsUrl;
	}

	public void setDetailsUrl(String detailsUrl) {
		this.detailsUrl = detailsUrl;
	}

	@Override
	public String toString() {
		return "{nasaId=" + nasaId + ", title=" + title + "}";
	}

}
