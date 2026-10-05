package com.redhat.mrp.client;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

@ApplicationScoped
public class MarsVistaHeadersFactory implements ClientHeadersFactory {

	@Inject
	@ConfigProperty(name = "api.key")
	String apiKey;

	@Override
	public MultivaluedMap<String, String> update(MultivaluedMap<String, String> incomingHeaders,
			MultivaluedMap<String, String> clientOutgoingHeaders) {
		MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
		headers.add("X-API-Key", apiKey);
		return headers;
	}

}
