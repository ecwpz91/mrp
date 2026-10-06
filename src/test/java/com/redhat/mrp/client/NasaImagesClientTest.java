package com.redhat.mrp.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.mrp.model.NasaLibraryImage;

class NasaImagesClientTest {

	private final ObjectMapper mapper = new ObjectMapper();
	private final NasaImagesClient client = new NasaImagesClient();

	@Test
	void mapItemPrefersMediumImageAndBuildsCreditAndDetailsLink() throws Exception {
		String json = """
				{
				  "data": [{
				    "nasa_id": "PIA19808",
				    "title": "Looking Up at Mars Rover Curiosity in Buckskin Selfie",
				    "description": "This low-angle self-portrait of NASA's Curiosity Mars rover.",
				    "center": "JPL",
				    "media_type": "image"
				  }],
				  "links": [
				    { "href": "https://images-assets.nasa.gov/image/PIA19808/PIA19808~thumb.jpg", "rel": "preview", "render": "image" },
				    { "href": "https://images-assets.nasa.gov/image/PIA19808/PIA19808~medium.jpg", "rel": "alternate", "render": "image" },
				    { "href": "https://images-assets.nasa.gov/image/PIA19808/PIA19808~large.jpg", "rel": "alternate", "render": "image" }
				  ]
				}
				""";
		JsonNode item = mapper.readTree(json);

		NasaLibraryImage image = client.mapItem(item);

		assertNotNull(image);
		assertEquals("PIA19808", image.getNasaId());
		assertEquals("Looking Up at Mars Rover Curiosity in Buckskin Selfie", image.getTitle());
		assertEquals("NASA / JPL", image.getCredit());
		assertEquals("https://images.nasa.gov/details-PIA19808", image.getDetailsUrl());
		assertTrue(image.getImageUrl().contains("~medium."));
		assertTrue(image.getCaption().contains("Curiosity"));
	}

	@Test
	void mapItemUsesPhotographerWhenPresent() throws Exception {
		String json = """
				{
				  "data": [{
				    "nasa_id": "TEST1",
				    "title": "Title",
				    "description": "Caption text",
				    "photographer": "Jane Doe",
				    "center": "JPL",
				    "media_type": "image"
				  }],
				  "links": [
				    { "href": "https://images-assets.nasa.gov/image/TEST1/TEST1~thumb.jpg", "rel": "preview", "render": "image" }
				  ]
				}
				""";

		NasaLibraryImage image = client.mapItem(mapper.readTree(json));

		assertEquals("Jane Doe", image.getCredit());
		assertTrue(image.getImageUrl().contains("~thumb."));
	}

}
