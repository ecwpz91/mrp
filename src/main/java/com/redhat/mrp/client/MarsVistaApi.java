package com.redhat.mrp.client;

import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

@Path("/api/v2")
@RegisterRestClient(configKey = "mars-vista")
@RegisterClientHeaders(MarsVistaHeadersFactory.class)
public interface MarsVistaApi {

	@GET
	@Path("/rovers")
	JsonNode listRovers();

	@GET
	@Path("/rovers/{slug}")
	JsonNode getRover(@PathParam("slug") String slug);

	@GET
	@Path("/photos")
	JsonNode listPhotos(@QueryParam("rovers") String rovers, @QueryParam("date_min") String dateMin,
			@QueryParam("date_max") String dateMax, @QueryParam("cameras") String cameras,
			@QueryParam("include") String include, @QueryParam("per_page") int perPage);

	@GET
	@Path("/photos/{id}")
	JsonNode getPhoto(@PathParam("id") long id, @QueryParam("include") String include);

}
