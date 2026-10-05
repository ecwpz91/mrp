package com.redhat.mrp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import com.redhat.mrp.client.MarsVistaClient;
import com.redhat.mrp.model.LandingSiteContext;
import com.redhat.mrp.model.Photo;
import com.redhat.mrp.model.Rover;

@Controller
@RequestMapping("/")
public class RoverController {

	private static final Logger LOGGER = LoggerFactory.getLogger(RoverController.class);
	private static final String FHAZ = "FHAZ";
	/** Prefix match for Perseverance (v2 cameras filter rejects FRONT_HAZCAM_* names). */
	private static final String PERSEVERANCE_FHAZ_PREFIX = "FRONT_HAZCAM";
	private static final int MAX_PHOTO_ATTEMPTS = 10;
	private static final Random RANDOM = new Random();

	private final MarsVistaClient marsVistaClient;

	public RoverController(MarsVistaClient marsVistaClient) {
		this.marsVistaClient = marsVistaClient;
	}

	@GetMapping("/rovers")
	public String findAllRovers(ModelMap model) {
		model.put("rovers", marsVistaClient.listRoversWithCameras());
		return "rovers";
	}

	@GetMapping("/rover/{name}")
	public String findRoverByName(ModelMap model, @PathVariable String name) {
		Rover result = marsVistaClient.getRover(name);
		if (result != null) {
			LOGGER.debug("Rover :: {}", result);
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

		if (photoId != null) {
			Photo photo = marsVistaClient.getPhotoById(photoId);
			if (photo == null) {
				throw new IllegalArgumentException("Photo " + photoId + " was not found.");
			}
			model.put("photo", photo);
			model.put("trek", LandingSiteContext.forRover(name));
			return "photo";
		}

		if (landingDate == null || maxDate == null) {
			throw new IllegalArgumentException(
					"Choose a random photo from the Rovers page, or open a photo link that includes photoId.");
		}

		Photo randomPhoto = pickRandomPhoto(name, landingDate, maxDate);
		String cam = cameraParamFor(name);
		return "redirect:/photo/" + name + "?earthDate=" + randomPhoto.getEarthDate() + "&photoId="
				+ randomPhoto.getId() + "&camera=" + cam;
	}

	private Photo pickRandomPhoto(String name, String landingDate, String maxDate) {
		DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;
		LocalDate from = LocalDate.parse(landingDate, formatter);
		LocalDate to = LocalDate.parse(maxDate, formatter);
		long days = from.until(to, ChronoUnit.DAYS);
		String camera = cameraFilterFor(name);

		LocalDate lastAttempt = null;
		for (int attempt = 0; attempt < MAX_PHOTO_ATTEMPTS; attempt++) {
			long randomDays = ThreadLocalRandom.current().nextLong(days + 1);
			lastAttempt = from.plusDays(randomDays);
			List<Photo> photos = marsVistaClient.listPhotosForDate(name, lastAttempt.toString(), camera);
			if (photos.isEmpty()) {
				LOGGER.debug("No {} photos for {} on {}; retrying", camera, name, lastAttempt);
				continue;
			}
			Photo randomPhoto = photos.get(RANDOM.nextInt(photos.size()));
			LOGGER.debug("Random photo picked :: {}", randomPhoto);
			return randomPhoto;
		}

		throw new IllegalArgumentException(
				"No photos available after " + MAX_PHOTO_ATTEMPTS + " attempts"
						+ (lastAttempt != null ? " (last date " + lastAttempt + ")" : "")
						+ ". You may want to try other dates.");
	}

	private static String cameraFilterFor(String roverName) {
		if ("perseverance".equalsIgnoreCase(roverName)) {
			return PERSEVERANCE_FHAZ_PREFIX;
		}
		return FHAZ;
	}

	private static String cameraParamFor(String roverName) {
		if ("perseverance".equalsIgnoreCase(roverName)) {
			return PERSEVERANCE_FHAZ_PREFIX;
		}
		return FHAZ;
	}

}
