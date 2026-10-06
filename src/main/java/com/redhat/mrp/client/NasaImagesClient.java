package com.redhat.mrp.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.redhat.mrp.model.NasaLibraryImage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Fetches curated mission stills from NASA Image and Video Library for rover detail pages.
 */
@ApplicationScoped
public class NasaImagesClient {

	private static final Logger LOGGER = Logger.getLogger(NasaImagesClient.class);
	private static final String MEDIA_TYPE_IMAGE = "image";
	private static final int PAGE_SIZE = 5;
	private static final int MAX_HIGHLIGHTS = 3;
	private static final int CAPTION_MAX_CHARS = 280;
	private static final String DETAILS_BASE = "https://images.nasa.gov/details-";

	private static final Map<String, List<String>> QUERIES_BY_ROVER;

	static {
		Map<String, List<String>> queries = new HashMap<>();
		queries.put("curiosity", List.of("Curiosity rover selfie", "Curiosity Mars landing", "Curiosity rover Gale crater"));
		queries.put("perseverance",
				List.of("Perseverance rover selfie", "Perseverance Mars landing", "Perseverance Ingenuity"));
		queries.put("opportunity",
				List.of("Opportunity rover selfie", "Opportunity Mars landing", "Opportunity rover Mars"));
		queries.put("spirit",
				List.of("Spirit Mars Exploration Rover", "Spirit rover landing", "Spirit Stretches Out"));
		QUERIES_BY_ROVER = Collections.unmodifiableMap(queries);
	}

	@Inject
	@RestClient
	NasaImagesApi nasaImagesApi;

	/**
	 * Mission portraits / landing / highlight stills for a rover slug (e.g. curiosity).
	 */
	public List<NasaLibraryImage> findMissionHighlights(String roverSlug) {
		List<String> queries = queriesFor(roverSlug);
		List<NasaLibraryImage> highlights = new ArrayList<>();
		Set<String> seenIds = new HashSet<>();

		for (String query : queries) {
			if (highlights.size() >= MAX_HIGHLIGHTS) {
				break;
			}
			JsonNode root = safeSearch(query);
			NasaLibraryImage image = firstUnusedImage(root, seenIds);
			if (image != null) {
				highlights.add(image);
			}
		}
		return highlights;
	}

	private List<String> queriesFor(String roverSlug) {
		if (roverSlug == null || roverSlug.isBlank()) {
			return List.of("Mars rover");
		}
		String key = roverSlug.toLowerCase(Locale.ROOT);
		List<String> curated = QUERIES_BY_ROVER.get(key);
		if (curated != null) {
			return curated;
		}
		return List.of(roverSlug + " Mars rover", roverSlug + " Mars landing");
	}

	private JsonNode safeSearch(String query) {
		try {
			return nasaImagesApi.search(query, MEDIA_TYPE_IMAGE, PAGE_SIZE);
		} catch (Exception e) {
			LOGGER.warnf(e, "NASA Images search failed for q=%s", query);
			return null;
		}
	}

	private NasaLibraryImage firstUnusedImage(JsonNode root, Set<String> seenIds) {
		if (root == null) {
			return null;
		}
		JsonNode items = root.path("collection").path("items");
		if (!items.isArray()) {
			return null;
		}
		for (JsonNode item : items) {
			NasaLibraryImage image = mapItem(item);
			if (image == null || image.getNasaId() == null || image.getImageUrl() == null) {
				continue;
			}
			if (seenIds.add(image.getNasaId())) {
				return image;
			}
		}
		return null;
	}

	NasaLibraryImage mapItem(JsonNode item) {
		if (item == null || item.isMissingNode() || item.isNull()) {
			return null;
		}
		JsonNode dataArr = item.path("data");
		if (!dataArr.isArray() || dataArr.isEmpty()) {
			return null;
		}
		JsonNode data = dataArr.get(0);
		String nasaId = text(data, "nasa_id");
		if (nasaId == null) {
			return null;
		}

		NasaLibraryImage image = new NasaLibraryImage();
		image.setNasaId(nasaId);
		image.setTitle(text(data, "title"));
		image.setCaption(truncate(text(data, "description"), CAPTION_MAX_CHARS));
		image.setCredit(formatCredit(text(data, "photographer"), text(data, "center")));
		image.setDetailsUrl(DETAILS_BASE + nasaId);
		image.setImageUrl(pickImageUrl(item.path("links")));
		return image;
	}

	private static String pickImageUrl(JsonNode links) {
		if (links == null || !links.isArray()) {
			return null;
		}
		String medium = null;
		String large = null;
		String preview = null;
		String any = null;
		for (JsonNode link : links) {
			if (!"image".equalsIgnoreCase(text(link, "render"))) {
				continue;
			}
			String href = text(link, "href");
			if (href == null) {
				continue;
			}
			any = href;
			String lower = href.toLowerCase(Locale.ROOT);
			if (lower.contains("~medium.")) {
				medium = href;
			} else if (lower.contains("~large.")) {
				large = href;
			} else if ("preview".equalsIgnoreCase(text(link, "rel")) || lower.contains("~thumb.")) {
				preview = href;
			}
		}
		if (medium != null) {
			return medium;
		}
		if (large != null) {
			return large;
		}
		if (preview != null) {
			return preview;
		}
		return any;
	}

	private static String formatCredit(String photographer, String center) {
		if (photographer != null && !photographer.isBlank()) {
			return photographer;
		}
		if (center != null && !center.isBlank()) {
			return "NASA / " + center;
		}
		return "NASA";
	}

	private static String truncate(String value, int maxChars) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.replaceAll("\\s+", " ").trim();
		if (normalized.length() <= maxChars) {
			return normalized;
		}
		int cut = normalized.lastIndexOf(' ', maxChars - 1);
		if (cut < maxChars / 2) {
			cut = maxChars - 1;
		}
		return normalized.substring(0, cut).trim() + "…";
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

}
