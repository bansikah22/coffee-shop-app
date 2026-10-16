package io.github.tandapnoelbansikah.coffeeshop.service.orders;

import java.math.BigDecimal;
import java.time.Instant;

import io.github.tandapnoelbansikah.coffeeshop.domain.CoffeeOrder;
import io.github.tandapnoelbansikah.coffeeshop.domain.FulfillmentType;

/** Captures committed order details needed to send its customer confirmation. */
record OrderPlacedEvent(Long orderId, String coffeeName, int quantity, BigDecimal totalPrice,
		Instant createdAt, String customerEmail, FulfillmentType fulfillmentType, String customerPhone,
		String deliveryAddress) {

	static OrderPlacedEvent from(CoffeeOrder order) {
		return new OrderPlacedEvent(order.getId(), order.getCoffeeName(), order.getQuantity(), order.getTotalPrice(),
				order.getCreatedAt(), order.getCustomerEmail(), order.getFulfillmentType(), order.getCustomerPhone(),
				order.getDeliveryAddress());
	}
}