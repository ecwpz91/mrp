package com.redhat.mrp.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Mars Trek landing-site orbital mosaic context for a rover mission.
 * Tiles are loaded by the browser from trek.nasa.gov (no API key).
 */
public class LandingSiteContext {

	private static final String TILE_BASE = "https://trek.nasa.gov/tiles/Mars/EQ/";
	private static final String VIKING_GLOBAL = "Mars_Viking_MDIM21_ClrMosaic_global_232m";

	private static final Map<String, LandingSiteContext> BY_ROVER;

	static {
		Map<String, LandingSiteContext> sites = new HashMap<>();
		// HiRISE landing mosaics (WGS84 bboxes from Trek WMTSCapabilities)
		sites.put("curiosity", site("curiosity", "Curiosity", "curiosity_hirise_mosaic",
				"HiRISE mosaic — Curiosity landing site", "png",
				137.1224669, -4.9254743, 137.7298129, -4.2489588));
		sites.put("spirit", site("spirit", "Spirit", "spirit_hirise_mosaic",
				"HiRISE mosaic — Spirit landing site", "png",
				175.4278511, -14.6925026, 175.5858031, -14.5357546));
		sites.put("opportunity", site("opportunity", "Opportunity", "opportunity_hirise_mosaic",
				"HiRISE mosaic — Opportunity landing site", "png",
				-5.5964391, -2.7513281, -4.9954112, -1.8994384));
		// No Perseverance HiRISE mosaic layer; use global Viking centered on Jezero
		sites.put("perseverance", site("perseverance", "Perseverance", VIKING_GLOBAL,
				"Viking color mosaic — Jezero crater (Perseverance)", "jpg",
				77.35, 18.35, 77.55, 18.55));
		BY_ROVER = Collections.unmodifiableMap(sites);
	}

	private final String roverKey;
	private final String roverDisplayName;
	private final String layerId;
	private final String layerTitle;
	private final String tileExtension;
	private final double minLon;
	private final double minLat;
	private final double maxLon;
	private final double maxLat;
	private final double centerLon;
	private final double centerLat;
	private final String tileUrlTemplate;
	private final String exploreUrl;

	private LandingSiteContext(String roverKey, String roverDisplayName, String layerId, String layerTitle,
			String tileExtension, double minLon, double minLat, double maxLon, double maxLat) {
		this.roverKey = roverKey;
		this.roverDisplayName = roverDisplayName;
		this.layerId = layerId;
		this.layerTitle = layerTitle;
		this.tileExtension = tileExtension;
		this.minLon = minLon;
		this.minLat = minLat;
		this.maxLon = maxLon;
		this.maxLat = maxLat;
		this.centerLon = (minLon + maxLon) / 2.0;
		this.centerLat = (minLat + maxLat) / 2.0;
		this.tileUrlTemplate = TILE_BASE + layerId + "/1.0.0/default/default028mm/{z}/{y}/{x}." + tileExtension;
		this.exploreUrl = "https://trek.nasa.gov/mars/?lat=" + centerLat + "&lon=" + centerLon;
	}

	private static LandingSiteContext site(String roverKey, String roverDisplayName, String layerId, String layerTitle,
			String tileExtension, double minLon, double minLat, double maxLon, double maxLat) {
		return new LandingSiteContext(roverKey, roverDisplayName, layerId, layerTitle, tileExtension, minLon, minLat,
				maxLon, maxLat);
	}

	/**
	 * Resolve landing-site Trek context for a rover name. Falls back to the global Viking mosaic.
	 */
	public static LandingSiteContext forRover(String roverName) {
		if (roverName == null || roverName.isBlank()) {
			return vikingFallback();
		}
		LandingSiteContext site = BY_ROVER.get(roverName.toLowerCase(Locale.ROOT));
		return site != null ? site : vikingFallback();
	}

	private static LandingSiteContext vikingFallback() {
		return site("unknown", "Mars", VIKING_GLOBAL, "Viking color mosaic — global Mars", "jpg", -180.0, -90.0, 180.0,
				90.0);
	}

	public String getRoverKey() {
		return roverKey;
	}

	public String getRoverDisplayName() {
		return roverDisplayName;
	}

	public String getLayerId() {
		return layerId;
	}

	public String getLayerTitle() {
		return layerTitle;
	}

	public String getTileExtension() {
		return tileExtension;
	}

	public double getMinLon() {
		return minLon;
	}

	public double getMinLat() {
		return minLat;
	}

	public double getMaxLon() {
		return maxLon;
	}

	public double getMaxLat() {
		return maxLat;
	}

	public double getCenterLon() {
		return centerLon;
	}

	public double getCenterLat() {
		return centerLat;
	}

	public String getTileUrlTemplate() {
		return tileUrlTemplate;
	}

	public String getExploreUrl() {
		return exploreUrl;
	}

	@Override
	public String toString() {
		return "{roverKey=" + roverKey + ", layerId=" + layerId + ", centerLon=" + centerLon + ", centerLat="
				+ centerLat + "}";
	}

}
