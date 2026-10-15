package io.github.tandapnoelbansikah.coffeeshop.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures the API description shown in OpenAPI documentation. Introduced in Git on 2026-10-13. */
@Configuration
class OpenApiConfiguration {

	@Bean
	OpenAPI coffeeShopOpenApi() {
		return new OpenAPI().info(new Info().title("Coffee Shop API").version("v1")
				.description("Catalog and order management API for the Coffee Shop POC."));
	}
}