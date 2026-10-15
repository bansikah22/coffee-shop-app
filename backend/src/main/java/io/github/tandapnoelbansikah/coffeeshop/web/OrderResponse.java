package io.github.tandapnoelbansikah.coffeeshop.web;

import java.math.BigDecimal;
import java.time.Instant;

import io.github.tandapnoelbansikah.coffeeshop.domain.CoffeeOrder;

/** Public response representation of a submitted order. Introduced in Git on 2026-10-13. */
record OrderResponse(Long id, String coffeeName, int quantity, BigDecimal totalPrice, Instant createdAt,
		String customerEmail) {

	static OrderResponse from(CoffeeOrder order) {
		return new OrderResponse(order.getId(), order.getCoffeeName(), order.getQuantity(), order.getTotalPrice(),
				order.getCreatedAt(), order.getCustomerEmail());
	}
}