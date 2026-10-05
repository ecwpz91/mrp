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
	 * Random selection (landingDate + maxDate) redirects once to a stable URL
	 * (earthDate + photoId + camera) so refresh keeps the same photo. Pick a new
	 * image from /rovers.
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

		if (landingDate == null || maxDate == null) {
			throw new IllegalArgumentException(
					"Choose a random photo from the Rovers page, or open a photo link that includes photoId.");
		}

		Photo randomPhoto = pickRandomPhoto(name, landingDate, maxDate);
		String cam = cameraParamFor(name);
		URI location = URI.create("/photo/" + name + "?earthDate=" + randomPhoto.getEarthDate() + "&photoId="
				+ randomPhoto.getId() + "&camera=" + cam);
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
