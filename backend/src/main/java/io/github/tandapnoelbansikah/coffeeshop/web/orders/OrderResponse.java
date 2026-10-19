package io.github.tandapnoelbansikah.coffeeshop.web.orders;

import java.math.BigDecimal;
import java.time.Instant;

import io.github.tandapnoelbansikah.coffeeshop.domain.CoffeeOrder;
import io.github.tandapnoelbansikah.coffeeshop.domain.FulfillmentType;
import io.github.tandapnoelbansikah.coffeeshop.domain.OrderStatus;

/** Public order details, including its fulfillment method and progress. */
record OrderResponse(Long id, String coffeeName, int quantity, BigDecimal totalPrice, Instant createdAt,
		String customerEmail, OrderStatus status, FulfillmentType fulfillmentType, String customerPhone,
		String deliveryAddress, String paymentAttemptId, String checkoutUrl) {

	static OrderResponse from(CoffeeOrder order) {
		return new OrderResponse(order.getId(), order.getCoffeeName(), order.getQuantity(), order.getTotalPrice(),
				order.getCreatedAt(), order.getCustomerEmail(), order.getStatus(), order.getFulfillmentType(),
				order.getCustomerPhone(), order.getDeliveryAddress(), order.getPaymentAttemptId(), null);
	}

	OrderResponse withCheckoutUrl(String url) {
		return new OrderResponse(id, coffeeName, quantity, totalPrice, createdAt, customerEmail, status,
				fulfillmentType, customerPhone, deliveryAddress, paymentAttemptId, url);
	}
}