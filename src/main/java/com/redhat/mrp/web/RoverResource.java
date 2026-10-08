package com.redhat.mrp.web;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.jboss.logging.Logger;

import com.redhat.mrp.client.MarsVistaClient;
import com.redhat.mrp.client.NasaImagesClient;
import com.redhat.mrp.model.LandingSiteContext;
import com.redhat.mrp.model.NasaLibraryImage;
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
	/**
	 * Spirit's last hazcam (2010-02-13) is ~17 days before max_date (2010-03-02); a short
	 * day-by-day walk misses it. One range query covers completed-mission gaps.
	 */
	private static final int ON_OR_BEFORE_LOOKBACK_DAYS = 60;
	private static final Random RANDOM = new Random();

	@Inject
	MarsVistaClient marsVistaClient;

	@Inject
	NasaImagesClient nasaImagesClient;

	@CheckedTemplate
	public static class Templates {
		public static native TemplateInstance rovers(List<Rover> rovers);

		public static native TemplateInstance rover(Rover rover, List<NasaLibraryImage> highlights);

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
		List<NasaLibraryImage> highlights = List.of();
		if (result != null) {
			LOGGER.debugf("Rover :: %s", result);
			highlights = nasaImagesClient.findMissionHighlights(result.getId() != null ? result.getId() : name);
		}
		return Templates.rover(result, highlights);
	}

	/**
	 * Photo selection redirects once to a stable URL (earthDate + photoId + camera)
	 * so refresh keeps the same image.
	 * <ul>
	 * <li>photoId — render that photo</li>
	 * <li>earthDate only — hazcam from that day (or latest hazcam within
	 * {@link #ON_OR_BEFORE_LOOKBACK_DAYS} if empty); used for Max/Recent from /rovers</li>
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
			selected = pickPhotoOnOrBefore(name, clampEarthDate(name, earthDate));
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

	/**
	 * Clamp earthDate into the rover's landing→max window. Safari date wheels often
	 * ignore HTML min/max; volunteers should still land on a mission day.
	 */
	private String clampEarthDate(String name, String earthDate) {
		DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;
		LocalDate day = LocalDate.parse(earthDate, formatter);
		Rover rover = marsVistaClient.getRover(name);
		if (rover == null) {
			return earthDate;
		}

		String landing = rover.getLandingDate();
		if (landing != null && !landing.isBlank()) {
			LocalDate landingDay = LocalDate.parse(landing, formatter);
			if (day.isBefore(landingDay)) {
				LOGGER.debugf("Clamping earthDate %s up to landing %s for %s", earthDate, landing, name);
				day = landingDay;
			}
		}

		String max = rover.getMaxDate();
		if (max != null && !max.isBlank()) {
			LocalDate maxDay = LocalDate.parse(max, formatter);
			if (day.isAfter(maxDay)) {
				LOGGER.debugf("Clamping earthDate %s down to max %s for %s", earthDate, max, name);
				day = maxDay;
			}
		}

		return day.toString();
	}

	/**
	 * Prefer hazcam photos on {@code earthDate}; if none, use the latest hazcam day within
	 * {@link #ON_OR_BEFORE_LOOKBACK_DAYS} (clamped to landing). Used for Max/Recent and
	 * calendar picks when the chosen day has no hazcam shots.
	 */
	private Photo pickPhotoOnOrBefore(String name, String earthDate) {
		DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;
		LocalDate day = LocalDate.parse(earthDate, formatter);
		String camera = cameraFilterFor(name);

		LocalDate from = day.minusDays(ON_OR_BEFORE_LOOKBACK_DAYS);
		Rover rover = marsVistaClient.getRover(name);
		if (rover != null && rover.getLandingDate() != null && !rover.getLandingDate().isBlank()) {
			LocalDate landing = LocalDate.parse(rover.getLandingDate(), formatter);
			if (from.isBefore(landing)) {
				from = landing;
			}
		}

		List<Photo> photos = marsVistaClient.listPhotosInRange(name, from.toString(), day.plusDays(1).toString(),
				camera);
		if (photos.isEmpty()) {
			throw new IllegalArgumentException(
					"No hazcam photos found on or shortly before " + earthDate + " for " + name + ".");
		}

		String latestDate = null;
		for (Photo photo : photos) {
			String photoDate = photo.getEarthDate();
			if (photoDate == null || photoDate.isBlank()) {
				continue;
			}
			if (latestDate == null || photoDate.compareTo(latestDate) > 0) {
				latestDate = photoDate;
			}
		}
		if (latestDate == null) {
			throw new IllegalArgumentException(
					"No hazcam photos found on or shortly before " + earthDate + " for " + name + ".");
		}

		List<Photo> onLatestDay = new ArrayList<>();
		for (Photo photo : photos) {
			if (latestDate.equals(photo.getEarthDate())) {
				onLatestDay.add(photo);
			}
		}
		Photo photo = onLatestDay.get(RANDOM.nextInt(onLatestDay.size()));
		LOGGER.debugf("On-or-before photo picked :: %s (requested %s)", photo, earthDate);
		return photo;
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
