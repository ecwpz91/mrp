package com.redhat.mrp.client;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.redhat.mrp.model.Camera;
import com.redhat.mrp.model.Photo;
import com.redhat.mrp.model.Rover;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Mars Vista API v2 client. Maps JSON:API-style payloads into view models.
 */
@ApplicationScoped
public class MarsVistaClient {

	private static final Logger LOGGER = Logger.getLogger(MarsVistaClient.class);
	private static final int DEFAULT_PER_PAGE = 100;

	@Inject
	@RestClient
	MarsVistaApi marsVistaApi;

	public List<Rover> listRoversWithCameras() {
		JsonNode root = safeCall(() -> marsVistaApi.listRovers());
		List<Rover> rovers = new ArrayList<>();
		if (root == null || !root.has("data")) {
			return rovers;
		}
		for (JsonNode item : root.get("data")) {
			String slug = text(item, "id");
			Rover detail = getRover(slug);
			if (detail != null) {
				rovers.add(detail);
			}
		}
		return rovers;
	}

	public Rover getRover(String slug) {
		JsonNode root = safeCall(() -> marsVistaApi.getRover(slug));
		if (root == null || !root.has("data")) {
			return null;
		}
		return mapRover(root.get("data"));
	}

	/**
	 * Photos for a single Earth date. Mars Vista v2 treats {@code date_max} as exclusive,
	 * so the query uses {@code [date, date+1)}.
	 */
	public List<Photo> listPhotosForDate(String roverSlug, String earthDate, String cameraFilter) {
		LocalDate day = LocalDate.parse(earthDate);
		return listPhotosInRange(roverSlug, day.toString(), day.plusDays(1).toString(), cameraFilter);
	}

	/**
	 * Photos in {@code [dateMin, dateMaxExclusive)}. Perseverance hazcam names are filtered
	 * client-side because the v2 {@code cameras} query rejects {@code FRONT_HAZCAM_*} ids.
	 */
	public List<Photo> listPhotosInRange(String roverSlug, String dateMin, String dateMaxExclusive,
			String cameraFilter) {
		boolean filterClientSide = "perseverance".equalsIgnoreCase(roverSlug);
		String cameras = (!filterClientSide && cameraFilter != null && !cameraFilter.isBlank()) ? cameraFilter : null;

		JsonNode root = safeCall(() -> marsVistaApi.listPhotos(roverSlug, dateMin, dateMaxExclusive, cameras,
				"rover,camera", DEFAULT_PER_PAGE));
		List<Photo> photos = mapPhotoList(root);
		if (filterClientSide && cameraFilter != null && !cameraFilter.isBlank()) {
			List<Photo> filtered = new ArrayList<>();
			for (Photo photo : photos) {
				if (photo.getCamera() != null && photo.getCamera().getId() != null
						&& photo.getCamera().getId().startsWith(cameraFilter)) {
					filtered.add(photo);
				}
			}
			return filtered;
		}
		return photos;
	}

	public Photo getPhotoById(long photoId) {
		JsonNode root = safeCall(() -> marsVistaApi.getPhoto(photoId, "rover,camera"));
		if (root == null || !root.has("data") || root.get("data").isNull()) {
			return null;
		}
		return mapPhoto(root.get("data"));
	}

	private JsonNode safeCall(ApiCall call) {
		try {
			return call.get();
		} catch (Exception e) {
			LOGGER.warnf(e, "Mars Vista request failed");
			return null;
		}
	}

	@FunctionalInterface
	private interface ApiCall {
		JsonNode get();
	}

	private List<Photo> mapPhotoList(JsonNode root) {
		List<Photo> photos = new ArrayList<>();
		if (root == null || !root.has("data") || !root.get("data").isArray()) {
			return photos;
		}
		for (JsonNode item : root.get("data")) {
			Photo photo = mapPhoto(item);
			if (photo != null) {
				photos.add(photo);
			}
		}
		return photos;
	}

	private Rover mapRover(JsonNode item) {
		if (item == null || item.isNull()) {
			return null;
		}
		Rover rover = new Rover();
		rover.setId(text(item, "id"));
		JsonNode attrs = item.path("attributes");
		rover.setName(text(attrs, "name"));
		rover.setLandingDate(text(attrs, "landing_date"));
		rover.setLaunchDate(text(attrs, "launch_date"));
		rover.setStatus(text(attrs, "status"));
		rover.setMaxSol(attrs.path("max_sol").asInt(0));
		rover.setMaxDate(text(attrs, "max_date"));
		rover.setTotalPhotos(attrs.path("total_photos").asInt(0));

		JsonNode camerasNode = item.path("relationships").path("cameras");
		if (camerasNode.isArray()) {
			List<Camera> cameras = new ArrayList<>();
			for (JsonNode camNode : camerasNode) {
				Camera camera = mapCamera(camNode);
				if (camera != null) {
					cameras.add(camera);
				}
			}
			rover.setCameras(cameras.toArray(new Camera[0]));
		}
		return rover;
	}

	private Photo mapPhoto(JsonNode item) {
		if (item == null || item.isNull()) {
			return null;
		}
		Photo photo = new Photo();
		photo.setId(item.path("id").asLong());
		JsonNode attrs = item.path("attributes");
		photo.setSol(attrs.path("sol").asLong(0));
		photo.setEarthDate(text(attrs, "earth_date"));
		photo.setTitle(text(attrs, "title"));
		photo.setCaption(text(attrs, "caption"));

		String imageUrl = text(attrs.path("images"), "full");
		if (imageUrl == null || imageUrl.isBlank()) {
			imageUrl = text(attrs.path("images"), "large");
		}
		if (imageUrl == null || imageUrl.isBlank()) {
			imageUrl = text(attrs.path("images"), "medium");
		}
		if (imageUrl == null || imageUrl.isBlank()) {
			imageUrl = text(attrs, "img_src");
		}
		photo.setImgSrc(toUrl(imageUrl));

		JsonNode relationships = item.path("relationships");
		photo.setCamera(mapCamera(relationships.path("camera")));
		JsonNode roverNode = relationships.path("rover");
		if (!roverNode.isMissingNode() && !roverNode.isNull()) {
			Rover rover = new Rover();
			rover.setId(text(roverNode, "id"));
			rover.setName(text(roverNode.path("attributes"), "name"));
			rover.setStatus(text(roverNode.path("attributes"), "status"));
			photo.setRover(rover);
		}
		return photo;
	}

	private Camera mapCamera(JsonNode camNode) {
		if (camNode == null || camNode.isMissingNode() || camNode.isNull()) {
			return null;
		}
		Camera camera = new Camera();
		camera.setId(text(camNode, "id"));
		JsonNode attrs = camNode.path("attributes");
		String name = text(attrs, "name");
		camera.setName(name != null ? name : camera.getId());
		camera.setFullName(text(attrs, "full_name"));
		return camera;
	}

	private static String text(JsonNode node, String field) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return null;
		}
		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			return null;
		}
		String text = value.asText(null);
		return text != null && !text.isBlank() ? text : null;
	}

	private static URL toUrl(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return new URL(value);
		} catch (MalformedURLException e) {
			return null;
		}
	}

}
