package io.github.tandapnoelbansikah.coffeeshop.configuration;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Applies the configured cross-origin policy to API routes. Introduced in Git on 2026-10-13. */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {

	private final List<String> allowedOrigins;

	public WebConfiguration(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOrigins(allowedOrigins.toArray(String[]::new))
				.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
				.allowedHeaders("Content-Type", "X-CSRF-TOKEN")
				.allowCredentials(true)
				.maxAge(3600);
	}
}