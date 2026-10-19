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
import org.springframework.web.bind.annotation.PathVariable;
import io.github.tandapnoelbansikah.coffeeshop.domain.CoffeeOrder;
import io.github.tandapnoelbansikah.coffeeshop.service.orders.OrderCheckoutCoordinator;
import io.github.tandapnoelbansikah.coffeeshop.domain.OrderStatus;

/** Exposes the order creation HTTP endpoint. Introduced in Git on 2026-10-13. */
@RestController
@RequestMapping("/api/v1/orders")
class OrderController {

	private final OrderService orderService;
	private final OrderCheckoutCoordinator checkoutCoordinator;

	OrderController(OrderService orderService, OrderCheckoutCoordinator checkoutCoordinator) {
		this.orderService = orderService;
		this.checkoutCoordinator = checkoutCoordinator;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request,
			@AuthenticationPrincipal UserAccount customer) {
		checkoutCoordinator.requireEnabled();
		CoffeeOrder order = orderService.createOrder(request.coffeeId(), request.quantity(),
				request.fulfillmentType(), request.customerPhone(), request.deliveryAddress(), customer);
		return checkoutResponse(order);
	}

	@PostMapping("/{orderId}/checkout")
	OrderResponse retryCheckout(@PathVariable Long orderId, @AuthenticationPrincipal UserAccount customer) {
		checkoutCoordinator.requireEnabled();
		CoffeeOrder order = orderService.findCustomerOrder(orderId, customer.getId());
		if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
			throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, "Order is not awaiting payment");
		}
		return checkoutResponse(order);
	}

	private OrderResponse checkoutResponse(CoffeeOrder order) {
		try {
		var checkout = checkoutCoordinator.start(order);
		return OrderResponse.from(orderService.findCustomerOrder(order.getId(), order.getCustomerId()))
				.withCheckoutUrl(checkout.checkoutUrl());
		} catch (org.springframework.web.server.ResponseStatusException unavailable) {
			if (unavailable.getStatusCode().is5xxServerError()) {
				return OrderResponse.from(order);
			}
			throw unavailable;
		}
	}

	@GetMapping
	List<OrderResponse> listCustomerOrders(@AuthenticationPrincipal UserAccount customer) {
		return orderService.listCustomerOrders(customer.getId()).stream().map(OrderResponse::from).toList();
	}

}