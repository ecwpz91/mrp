package com.redhat.mrp.web;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.jboss.logging.Logger;

import com.redhat.mrp.client.MarsVistaClient;
import com.redhat.mrp.model.LandingSiteContext;
import com.redhat.mrp.model.Photo;
import com.redhat.mrp.model.Rover;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/")
public class RoverResource {

	private static final Logger LOGGER = Logger.getLogger(RoverResource.class);
	private static final String FHAZ = "FHAZ";
	/** Prefix match for Perseverance (v2 cameras filter rejects FRONT_HAZCAM_* names). */
	private static final String PERSEVERANCE_FHAZ_PREFIX = "FRONT_HAZCAM";
	private static final int MAX_PHOTO_ATTEMPTS = 10;
	private static final Random RANDOM = new Random();

	@Inject
	MarsVistaClient marsVistaClient;

	@CheckedTemplate
	public static class Templates {
		public static native TemplateInstance rovers(List<Rover> rovers);

		public static native TemplateInstance rover(Rover rover);

		public static native TemplateInstance photo(Photo photo, LandingSiteContext trek);
	}

	@GET
	@Path("/rovers")
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance findAllRovers() {
		return Templates.rovers(marsVistaClient.listRoversWithCameras());
	}

	@GET
	@Path("/rover/{name}")
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance findRoverByName(@PathParam("name") String name) {
		Rover result = marsVistaClient.getRover(name);
		if (result != null) {
			LOGGER.debugf("Rover :: %s", result);
		}
		return Templates.rover(result);
	}

	/**
	 * Photo selection redirects once to a stable URL (earthDate + photoId + camera)
	 * so refresh keeps the same image.
	 * <ul>
	 * <li>photoId — render that photo</li>
	 * <li>earthDate only — hazcam from that day (walks back a few days if empty);
	 * used for Recent/Last Photo from /rovers</li>
	 * <li>landingDate + maxDate — random day in range; Random Photo from /rovers</li>
	 * </ul>
	 */
	@GET
	@Path("/photo/{name}")
	@Produces(MediaType.TEXT_HTML)
	public Object getPhoto(@PathParam("name") String name, @QueryParam("landingDate") String landingDate,
			@QueryParam("maxDate") String maxDate, @QueryParam("earthDate") String earthDate,
			@QueryParam("photoId") Long photoId, @QueryParam("camera") String camera) {

		if (photoId != null) {
			Photo photo = marsVistaClient.getPhotoById(photoId);
			if (photo == null) {
				throw new IllegalArgumentException("Photo " + photoId + " was not found.");
			}
			return Templates.photo(photo, LandingSiteContext.forRover(name));
		}

		Photo selected;
		if (earthDate != null && !earthDate.isBlank()) {
			selected = pickPhotoOnOrBefore(name, earthDate);
		} else if (landingDate != null && maxDate != null) {
			selected = pickRandomPhoto(name, landingDate, maxDate);
		} else {
			throw new IllegalArgumentException(
					"Choose a photo from the Rovers page, or open a photo link that includes photoId.");
		}

		String cam = cameraParamFor(name);
		URI location = URI.create("/photo/" + name + "?earthDate=" + selected.getEarthDate() + "&photoId="
				+ selected.getId() + "&camera=" + cam);
		return Response.seeOther(location).build();
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
				LOGGER.debugf("No %s photos for %s on %s; retrying", camera, name, lastAttempt);
				continue;
			}
			Photo randomPhoto = photos.get(RANDOM.nextInt(photos.size()));
			LOGGER.debugf("Random photo picked :: %s", randomPhoto);
			return randomPhoto;
		}

		throw new IllegalArgumentException(
				"No photos available after " + MAX_PHOTO_ATTEMPTS + " attempts"
						+ (lastAttempt != null ? " (last date " + lastAttempt + ")" : "")
						+ ". You may want to try other dates.");
	}

	/** Prefer hazcam photos on earthDate; walk back up to {@link #MAX_PHOTO_ATTEMPTS} days. */
	private Photo pickPhotoOnOrBefore(String name, String earthDate) {
		DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;
		LocalDate day = LocalDate.parse(earthDate, formatter);
		String camera = cameraFilterFor(name);

		for (int attempt = 0; attempt < MAX_PHOTO_ATTEMPTS; attempt++) {
			LocalDate candidate = day.minusDays(attempt);
			List<Photo> photos = marsVistaClient.listPhotosForDate(name, candidate.toString(), camera);
			if (photos.isEmpty()) {
				LOGGER.debugf("No %s photos for %s on %s; walking back", camera, name, candidate);
				continue;
			}
			Photo photo = photos.get(RANDOM.nextInt(photos.size()));
			LOGGER.debugf("On-or-before photo picked :: %s", photo);
			return photo;
		}

		throw new IllegalArgumentException(
				"No hazcam photos found on or shortly before " + earthDate + " for " + name + ".");
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
