package io.github.tandapnoelbansikah.coffeeshop.service.orders;

import io.github.tandapnoelbansikah.coffeeshop.domain.CoffeeOrder;
import io.github.tandapnoelbansikah.coffeeshop.domain.FulfillmentType;

/** Captures a completed order for its final customer notification. */
record OrderCompletedEvent(Long orderId, String coffeeName, int quantity, java.math.BigDecimal totalPrice,
		String customerEmail, FulfillmentType fulfillmentType) {

	static OrderCompletedEvent from(CoffeeOrder order) {
		return new OrderCompletedEvent(order.getId(), order.getCoffeeName(), order.getQuantity(), order.getTotalPrice(),
				order.getCustomerEmail(), order.getFulfillmentType());
	}
}