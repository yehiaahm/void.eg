package com.voidstore.api.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serves uploaded product images from the uploads directory at /uploads/**. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final AppProperties props;

	public WebConfig(AppProperties props) {
		this.props = props;
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String location = Path.of(props.uploadsDir()).toAbsolutePath().normalize().toUri().toString();
		registry.addResourceHandler("/uploads/**")
				.addResourceLocations(location.endsWith("/") ? location : location + "/")
				// file names are content hashes, so they never change
				.setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
	}
}
