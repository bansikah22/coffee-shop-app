package io.github.tandapnoelbansikah.coffeeshop.web;

import java.math.BigDecimal;

import io.github.tandapnoelbansikah.coffeeshop.domain.Coffee;

/** Public response representation of a coffee catalog item. Introduced in Git on 2026-10-13. */
record CoffeeResponse(Long id, String name, String description, BigDecimal price, boolean available) {

	static CoffeeResponse from(Coffee coffee) {
		return new CoffeeResponse(coffee.getId(), coffee.getName(), coffee.getDescription(), coffee.getPrice(),
				coffee.isAvailable());
	}
}