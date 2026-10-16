package io.github.tandapnoelbansikah.coffeeshop.web.orders;

import io.github.tandapnoelbansikah.coffeeshop.service.orders.OrderService;
import io.github.tandapnoelbansikah.coffeeshop.domain.UserAccount;
import jakarta.validation.Valid;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the order creation HTTP endpoint. Introduced in Git on 2026-10-13. */
@RestController
@RequestMapping("/api/v1/orders")
class OrderController {

	private final OrderService orderService;

	OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request,
			@AuthenticationPrincipal UserAccount customer) {
		return OrderResponse.from(orderService.createOrder(request.coffeeId(), request.quantity(),
				request.fulfillmentType(), request.customerPhone(), request.deliveryAddress(), customer));
	}

	@GetMapping
	List<OrderResponse> listCustomerOrders(@AuthenticationPrincipal UserAccount customer) {
		return orderService.listCustomerOrders(customer.getId()).stream().map(OrderResponse::from).toList();
	}

}