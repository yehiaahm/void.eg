package com.voidstore.api.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.voidstore.api.auth.JwtService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
	private static final String DEV_SECRET_MARKER = "dev-only";

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.csrf(c -> c.disable()) // bearer tokens; the refresh cookie is SameSite=Strict and path-scoped
				.cors(c -> {})
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.headers(h -> h
						.contentTypeOptions(c -> {})
						.frameOptions(f -> f.deny())
						.referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
				.authorizeHttpRequests(a -> a
						.requestMatchers("/actuator/health", "/uploads/**", "/error").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/products/**", "/api/v1/settings", "/api/v1/pages/**",
								"/api/v1/shipping-zones", "/api/v1/sitemap.xml").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/orders", "/api/v1/orders/track", "/api/v1/orders/quote",
								"/api/v1/orders/cancel", "/api/v1/orders/returns", "/api/v1/waitlist", "/api/v1/contact").permitAll()
						.requestMatchers("/api/v1/auth/**").permitAll()
						.requestMatchers("/api/v1/admin/**").hasAnyRole("STAFF", "ADMIN", "OWNER")
						// storefront pages, static assets, sitemap (served by Spring in production)
						.requestMatchers(r -> !r.getRequestURI().startsWith("/api/")).permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(o -> o
						.jwt(j -> j.jwtAuthenticationConverter(jwtAuthConverter()))
						.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
		return http.build();
	}

	/** `role` claim → ROLE_xxx authority. */
	private JwtAuthenticationConverter jwtAuthConverter() {
		var c = new JwtAuthenticationConverter();
		c.setJwtGrantedAuthoritiesConverter(jwt -> {
			String role = jwt.getClaimAsString("role");
			return role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
		});
		return c;
	}

	@Bean
	SecretKey jwtKey(AppProperties props) {
		String secret = props.jwt().secret();
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException("void.jwt.secret (JWT_SECRET) must be at least 32 bytes");
		}
		if (secret.contains(DEV_SECRET_MARKER)) {
			log.warn("Using the development JWT secret — set JWT_SECRET in production!");
		}
		return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey key) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(key));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey key) {
		var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtService.ISSUER));
		return decoder;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(AppProperties props) {
		var cfg = new CorsConfiguration();
		cfg.setAllowedOrigins(List.of(props.frontendUrl().split("\\s*,\\s*")));
		cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		cfg.setAllowCredentials(true);
		cfg.setMaxAge(3600L);
		var source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", cfg);
		return source;
	}
}
