package io.github.tandapnoelbansikah.coffeeshop.service.orders;

import java.math.BigDecimal;

import io.github.tandapnoelbansikah.coffeeshop.domain.CoffeeOrder;
import io.github.tandapnoelbansikah.coffeeshop.domain.FulfillmentType;

/** Captures committed fulfillment details needed for the pickup-ready or dispatch email. */
record OrderReadyEvent(Long orderId, String coffeeName, int quantity, BigDecimal totalPrice, String customerEmail,
		FulfillmentType fulfillmentType, String customerPhone, String deliveryAddress) {

	static OrderReadyEvent from(CoffeeOrder order) {
		return new OrderReadyEvent(order.getId(), order.getCoffeeName(), order.getQuantity(), order.getTotalPrice(),
				order.getCustomerEmail(), order.getFulfillmentType(), order.getCustomerPhone(), order.getDeliveryAddress());
	}
}