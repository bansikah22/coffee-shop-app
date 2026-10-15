package io.github.tandapnoelbansikah.coffeeshop.service;

import io.github.tandapnoelbansikah.coffeeshop.domain.Coffee;
import io.github.tandapnoelbansikah.coffeeshop.domain.CoffeeOrder;
import io.github.tandapnoelbansikah.coffeeshop.domain.UserAccount;
import io.github.tandapnoelbansikah.coffeeshop.repository.CoffeeOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** Creates orders from available catalog items and persists price snapshots. Introduced in Git on 2026-10-13. */
@Service
public class OrderService {

	private final CoffeeCatalogService coffeeCatalogService;
	private final CoffeeOrderRepository coffeeOrderRepository;

	public OrderService(CoffeeCatalogService coffeeCatalogService, CoffeeOrderRepository coffeeOrderRepository) {
		this.coffeeCatalogService = coffeeCatalogService;
		this.coffeeOrderRepository = coffeeOrderRepository;
	}

	@Transactional
	public CoffeeOrder createOrder(Long coffeeId, int quantity, UserAccount customer) {
		Coffee coffee = coffeeCatalogService.findAvailableCoffee(coffeeId);
		return coffeeOrderRepository.save(new CoffeeOrder(coffee.getId(), coffee.getName(), quantity, coffee.getPrice(),
				customer.getId(), customer.getEmail()));
	}

	@Transactional(readOnly = true)
	public List<CoffeeOrder> listCustomerOrders(Long customerId) {
		return coffeeOrderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
	}

	@Transactional(readOnly = true)
	public List<CoffeeOrder> listAllOrders() {
		return coffeeOrderRepository.findAllByOrderByCreatedAtDesc();
	}
}