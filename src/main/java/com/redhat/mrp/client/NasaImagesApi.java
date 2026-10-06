package com.redhat.mrp.client;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

/**
 * NASA Image and Video Library search API (no API key required).
 *
 * @see <a href="https://images.nasa.gov/docs/images.nasa.gov_api_docs.pdf">API docs</a>
 */
@Path("/")
@RegisterRestClient(configKey = "nasa-images")
public interface NasaImagesApi {

	@GET
	@Path("/search")
	JsonNode search(@QueryParam("q") String query, @QueryParam("media_type") String mediaType,
			@QueryParam("page_size") int pageSize);

}
