package io.github.tandapnoelbansikah.coffeeshop.service.orders;

import io.github.tandapnoelbansikah.coffeeshop.domain.Coffee;
import io.github.tandapnoelbansikah.coffeeshop.domain.CoffeeOrder;
import io.github.tandapnoelbansikah.coffeeshop.domain.FulfillmentType;
import io.github.tandapnoelbansikah.coffeeshop.domain.OrderStatus;
import io.github.tandapnoelbansikah.coffeeshop.domain.UserAccount;
import io.github.tandapnoelbansikah.coffeeshop.repository.CoffeeOrderRepository;
import io.github.tandapnoelbansikah.coffeeshop.service.catalog.CoffeeCatalogService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

/** Creates customer orders, persists fulfillment snapshots, and advances fulfillment status. */
@Service
public class OrderService {

	private final CoffeeCatalogService coffeeCatalogService;
	private final CoffeeOrderRepository coffeeOrderRepository;
	private final ApplicationEventPublisher events;

	public OrderService(CoffeeCatalogService coffeeCatalogService, CoffeeOrderRepository coffeeOrderRepository,
			ApplicationEventPublisher events) {
		this.coffeeCatalogService = coffeeCatalogService;
		this.coffeeOrderRepository = coffeeOrderRepository;
		this.events = events;
	}

	@Transactional
	public CoffeeOrder createOrder(Long coffeeId, int quantity, FulfillmentType fulfillmentType, String customerPhone,
			String deliveryAddress, UserAccount customer) {
		Coffee coffee = coffeeCatalogService.findAvailableCoffee(coffeeId);
		CoffeeOrder order = coffeeOrderRepository.save(new CoffeeOrder(coffee.getId(), coffee.getName(), quantity,
				coffee.getPrice(), customer.getId(), customer.getEmail(), fulfillmentType, customerPhone, deliveryAddress));
		events.publishEvent(OrderPlacedEvent.from(order));
		return order;
	}

	@Transactional(readOnly = true)
	public List<CoffeeOrder> listCustomerOrders(Long customerId) {
		return coffeeOrderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
	}

	@Transactional(readOnly = true)
	public List<CoffeeOrder> listAllOrders() {
		return coffeeOrderRepository.findAllByOrderByCreatedAtDesc();
	}

	@Transactional
	public CoffeeOrder markOrderReady(Long orderId) {
		CoffeeOrder order = coffeeOrderRepository.findById(orderId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
		if (order.markReadyForFulfillment()) {
			events.publishEvent(OrderReadyEvent.from(order));
		}
		return order;
	}

	@Transactional
	public CoffeeOrder markOrderCompleted(Long orderId) {
		CoffeeOrder order = coffeeOrderRepository.findById(orderId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
		if (order.markCompleted()) {
			events.publishEvent(OrderCompletedEvent.from(order));
		}
		return order;
	}
}