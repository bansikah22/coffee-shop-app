package io.github.tandapnoelbansikah.coffeeshop.web.orders;

import java.util.List;

import io.github.tandapnoelbansikah.coffeeshop.domain.CoffeeOrder;
import io.github.tandapnoelbansikah.coffeeshop.service.orders.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Provides administrator-only order review and fulfillment transitions. */
@RestController
@RequestMapping("/api/v1/admin/orders")
class AdminOrderController {

	private final OrderService orderService;

	AdminOrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@GetMapping
	List<OrderResponse> listAllOrders() {
		return orderService.listAllOrders().stream().map(OrderResponse::from).toList();
	}

	@PatchMapping("/{orderId}/ready")
	OrderResponse markOrderReady(@PathVariable Long orderId) {
		CoffeeOrder order = orderService.markOrderReady(orderId);
		return OrderResponse.from(order);
	}

	@PatchMapping("/{orderId}/complete")
	OrderResponse markOrderCompleted(@PathVariable Long orderId) {
		return OrderResponse.from(orderService.markOrderCompleted(orderId));
	}
}