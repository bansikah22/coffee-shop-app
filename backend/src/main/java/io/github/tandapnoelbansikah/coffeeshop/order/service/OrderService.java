package io.github.tandapnoelbansikah.coffeeshop.order.service;

import io.github.tandapnoelbansikah.coffeeshop.catalog.domain.Coffee;
import io.github.tandapnoelbansikah.coffeeshop.catalog.service.CoffeeCatalogService;
import io.github.tandapnoelbansikah.coffeeshop.order.domain.CoffeeOrder;
import io.github.tandapnoelbansikah.coffeeshop.order.repository.CoffeeOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

	private final CoffeeCatalogService coffeeCatalogService;
	private final CoffeeOrderRepository coffeeOrderRepository;

	public OrderService(CoffeeCatalogService coffeeCatalogService, CoffeeOrderRepository coffeeOrderRepository) {
		this.coffeeCatalogService = coffeeCatalogService;
		this.coffeeOrderRepository = coffeeOrderRepository;
	}

	@Transactional
	public CoffeeOrder createOrder(Long coffeeId, int quantity) {
		Coffee coffee = coffeeCatalogService.findAvailableCoffee(coffeeId);
		return coffeeOrderRepository.save(new CoffeeOrder(coffee.getId(), coffee.getName(), quantity, coffee.getPrice()));
	}
}