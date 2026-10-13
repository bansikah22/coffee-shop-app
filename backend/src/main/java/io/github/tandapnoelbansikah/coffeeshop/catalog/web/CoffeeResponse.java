package io.github.tandapnoelbansikah.coffeeshop.catalog.web;

import java.math.BigDecimal;

import io.github.tandapnoelbansikah.coffeeshop.catalog.domain.Coffee;

record CoffeeResponse(Long id, String name, String description, BigDecimal price, boolean available) {

	static CoffeeResponse from(Coffee coffee) {
		return new CoffeeResponse(coffee.getId(), coffee.getName(), coffee.getDescription(), coffee.getPrice(),
				coffee.isAvailable());
	}
}