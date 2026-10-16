package io.github.tandapnoelbansikah.coffeeshop.web.catalog;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Validated request payload for creating or updating a coffee. Introduced in Git on 2026-10-13. */
record CoffeeRequest(
		@NotBlank @Size(max = 100) String name,
		@NotBlank @Size(max = 500) String description,
		@NotNull @DecimalMin(value = "0.00") BigDecimal price,
		@NotNull Boolean available) {
}