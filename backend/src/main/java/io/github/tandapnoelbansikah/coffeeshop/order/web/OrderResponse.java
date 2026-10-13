package io.github.tandapnoelbansikah.coffeeshop.order.web;

import java.math.BigDecimal;
import java.time.Instant;

import io.github.tandapnoelbansikah.coffeeshop.order.domain.CoffeeOrder;

record OrderResponse(Long id, String coffeeName, int quantity, BigDecimal totalPrice, Instant createdAt) {

	static OrderResponse from(CoffeeOrder order) {
		return new OrderResponse(order.getId(), order.getCoffeeName(), order.getQuantity(), order.getTotalPrice(),
				order.getCreatedAt());
	}
}