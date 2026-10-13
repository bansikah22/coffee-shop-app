package io.github.tandapnoelbansikah.coffeeshop.catalog.web;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record CoffeeRequest(
		@NotBlank @Size(max = 100) String name,
		@NotBlank @Size(max = 500) String description,
		@NotNull @DecimalMin(value = "0.00") BigDecimal price,
		@NotNull Boolean available) {
}