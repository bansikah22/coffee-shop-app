package io.github.tandapnoelbansikah.coffeeshop.web;

import java.util.List;

import io.github.tandapnoelbansikah.coffeeshop.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Provides administrator-only access to all submitted orders. */
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
}