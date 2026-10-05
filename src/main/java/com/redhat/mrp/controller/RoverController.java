package com.redhat.mrp.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.mrp.model.LandingSiteContext;
import com.redhat.mrp.model.Photo;
import com.redhat.mrp.model.PhotoList;
import com.redhat.mrp.model.Rover;
import com.redhat.mrp.model.RoverList;
import com.redhat.mrp.model.RoverResponse;

@Controller
@RequestMapping("/")
public class RoverController {

	private final RestTemplate restTemplate;
	private final ObjectMapper objectMapper;

	private static final String URI = "https://api.marsvista.dev/api/v1/rovers";
	private static final String API_KEY_HEADER = "X-API-Key";
	private static final Logger LOGGER = LoggerFactory.getLogger(RoverController.class);
	private static final String FHAZ = "FHAZ";
	private static final String PERSEVERANCE_FHAZ = "FRONT_HAZCAM_LEFT_A";
	private static final int MAX_PHOTO_ATTEMPTS = 10;
	private static final Random RANDOM = new Random();

	@Value("${api.key}")
	private String apiKey;

	public RoverController(RestTemplate restTemplate, ObjectMapper objectMapper) {
		this.restTemplate = restTemplate;
		this.objectMapper = objectMapper;
	}

	@GetMapping("/rovers")
	public String findAllRovers(ModelMap model) {
		String uriString = UriComponentsBuilder.fromHttpUrl(URI).toUriString();
		ResponseEntity<RoverList> response = this.restTemplate.exchange(uriString, HttpMethod.GET,
				authorizedEntity(), RoverList.class);
		Optional<RoverList> result = Optional.ofNullable(response.getBody());
		List<Rover> rovers = result.map(RoverList::getRovers).map(List::of).orElseGet(ArrayList::new);

		model.put("rovers", rovers);
		return "rovers";
	}

	@GetMapping("/rover/{name}")
	public String findRoverByName(ModelMap model, @PathVariable String name) {
		String uriString = UriComponentsBuilder.fromHttpUrl(URI + "/" + name).toUriString();
		ResponseEntity<RoverResponse> response = this.restTemplate.exchange(uriString, HttpMethod.GET,
				authorizedEntity(), RoverResponse.class);
		Rover result = Optional.ofNullable(response.getBody()).map(RoverResponse::getRover).orElse(null);

		if (result != null) {
			LOGGER.debug("Response body :: {}", result);
			try {
				LOGGER.debug("Rover :: {}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
			} catch (Exception e) {
				LOGGER.debug("Exception :: {}", e);
			}
		}

		model.put("rover", result);
		return "rover";
	}

	/**
	 * Random selection (landingDate + maxDate) redirects once to a stable URL
	 * (earthDate + photoId + camera) so refresh keeps the same photo. Pick a new
	 * image from /rovers.
	 */
	@GetMapping("/photo/{name}")
	public String getPhoto(ModelMap model, @PathVariable String name,
			@RequestParam(value = "landingDate", required = false) String landingDate,
			@RequestParam(value = "maxDate", required = false) String maxDate,
			@RequestParam(value = "earthDate", required = false) String earthDate,
			@RequestParam(value = "photoId", required = false) Long photoId,
			@RequestParam(value = "camera", required = false) String camera) {

		if (photoId != null && earthDate != null && !earthDate.isBlank()) {
			String cam = (camera != null && !camera.isBlank()) ? camera : frontHazcamFor(name);
			Photo photo = findPhotoById(earthDate, name, cam, photoId);
			if (photo == null) {
				throw new IllegalArgumentException(
						"Photo " + photoId + " was not found for " + name + " on " + earthDate + ".");
			}
			model.put("photo", photo);
			model.put("trek", LandingSiteContext.forRover(name));
			return "photo";
		}

		if (landingDate == null || maxDate == null) {
			throw new IllegalArgumentException(
					"Choose a random photo from the Rovers page, or open a photo link that includes earthDate and photoId.");
		}

		Photo randomPhoto = pickRandomPhoto(name, landingDate, maxDate);
		String cam = frontHazcamFor(name);
		return "redirect:/photo/" + name + "?earthDate=" + randomPhoto.getEarthDate() + "&photoId="
				+ randomPhoto.getId() + "&camera=" + cam;
	}

	private Photo pickRandomPhoto(String name, String landingDate, String maxDate) {
		DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;
		LocalDate from = LocalDate.parse(landingDate, formatter);
		LocalDate to = LocalDate.parse(maxDate, formatter);
		long days = from.until(to, ChronoUnit.DAYS);
		String camera = frontHazcamFor(name);

		LocalDate lastAttempt = null;
		for (int attempt = 0; attempt < MAX_PHOTO_ATTEMPTS; attempt++) {
			long randomDays = ThreadLocalRandom.current().nextLong(days + 1);
			lastAttempt = from.plusDays(randomDays);
			PhotoList photosForDate = getAllPhotos(lastAttempt.toString(), name, camera);
			if (photosForDate == null || photosForDate.getPhotos() == null || photosForDate.getPhotos().length == 0) {
				LOGGER.debug("No {} photos for {} on {}; retrying", camera, name, lastAttempt);
				continue;
			}
			Photo[] photos = photosForDate.getPhotos();
			Photo randomPhoto = photos[RANDOM.nextInt(photos.length)];
			LOGGER.debug("Random photo picked :: {}", randomPhoto);
			return randomPhoto;
		}

		throw new IllegalArgumentException(
				"No photos available after " + MAX_PHOTO_ATTEMPTS + " attempts"
						+ (lastAttempt != null ? " (last date " + lastAttempt + ")" : "")
						+ ". You may want to try other dates.");
	}

	private Photo findPhotoById(String earthDate, String name, String camera, long photoId) {
		PhotoList photosForDate = getAllPhotos(earthDate, name, camera);
		if (photosForDate == null || photosForDate.getPhotos() == null) {
			return null;
		}
		for (Photo photo : photosForDate.getPhotos()) {
			if (photo.getId() == photoId) {
				return photo;
			}
		}
		return null;
	}

	public PhotoList getAllPhotos(String date, String name, String camera) {
		UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(URI + "/" + name + "/photos")
				.queryParam("earth_date", date).queryParam("camera", camera);
		String uriString = builder.toUriString();
		LOGGER.debug("Fetching photos from URI :: {}", uriString);
		ResponseEntity<String> result = restTemplate.exchange(uriString, HttpMethod.GET, authorizedEntity(),
				String.class);

		try {
			PhotoList photos = objectMapper.readValue(result.getBody(), PhotoList.class);
			LOGGER.debug("PhotoList :: {}", photos);
			return photos;
		} catch (Exception e) {
			LOGGER.debug("Exception :: {}", e);
			return null;
		}
	}

	private static String frontHazcamFor(String roverName) {
		if ("perseverance".equalsIgnoreCase(roverName)) {
			return PERSEVERANCE_FHAZ;
		}
		return FHAZ;
	}

	private HttpEntity<Void> authorizedEntity() {
		HttpHeaders headers = new HttpHeaders();
		headers.set(API_KEY_HEADER, apiKey);
		return new HttpEntity<>(headers);
	}

}
