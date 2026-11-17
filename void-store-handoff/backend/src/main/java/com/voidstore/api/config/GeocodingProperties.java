package com.voidstore.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * "Use my location" at checkout (`void.geocoding.*`): the browser sends the shopper's coordinates to this API, which asks an
 * OpenStreetMap Nominatim server for the address. Nothing is stored or logged. Point {@code url} at your own Nominatim
 * (or a compatible service) for heavy traffic: the public server allows about one request per second.
 */
@ConfigurationProperties("void.geocoding")
public record GeocodingProperties(boolean enabled, String url) {}
