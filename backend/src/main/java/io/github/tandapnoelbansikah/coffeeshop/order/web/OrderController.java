package io.github.tandapnoelbansikah.coffeeshop.order.web;

import io.github.tandapnoelbansikah.coffeeshop.order.service.OrderService;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
class OrderController {

	private final OrderService orderService;

	OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
		return OrderResponse.from(orderService.createOrder(request.coffeeId(), request.quantity()));
	}
}