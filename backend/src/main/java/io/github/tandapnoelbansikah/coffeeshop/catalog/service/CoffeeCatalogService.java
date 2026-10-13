package io.github.tandapnoelbansikah.coffeeshop.catalog.service;

import java.util.List;

import io.github.tandapnoelbansikah.coffeeshop.catalog.domain.Coffee;
import io.github.tandapnoelbansikah.coffeeshop.catalog.repository.CoffeeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CoffeeCatalogService {

	private final CoffeeRepository coffeeRepository;

	public CoffeeCatalogService(CoffeeRepository coffeeRepository) {
		this.coffeeRepository = coffeeRepository;
	}

	public List<Coffee> listCoffees() {
		return coffeeRepository.findAllByOrderByNameAsc();
	}

	public Coffee findAvailableCoffee(Long coffeeId) {
		Coffee coffee = findCoffee(coffeeId);
		if (!coffee.isAvailable()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Coffee not found");
		}
		return coffee;
	}

	public Coffee findCoffee(Long coffeeId) {
		return coffeeRepository.findById(coffeeId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coffee not found"));
	}

	@Transactional
	public Coffee createCoffee(String name, String description, java.math.BigDecimal price, boolean available) {
		assertNameAvailable(name, null);
		return coffeeRepository.save(new Coffee(name, description, price, available));
	}

	@Transactional
	public Coffee updateCoffee(Long coffeeId, String name, String description, java.math.BigDecimal price, boolean available) {
		Coffee coffee = findCoffee(coffeeId);
		assertNameAvailable(name, coffeeId);
		coffee.update(name, description, price, available);
		return coffee;
	}

	@Transactional
	public void deleteCoffee(Long coffeeId) {
		coffeeRepository.delete(findCoffee(coffeeId));
	}

	private void assertNameAvailable(String name, Long coffeeId) {
		boolean duplicate = coffeeId == null ? coffeeRepository.existsByNameIgnoreCase(name)
				: coffeeRepository.existsByNameIgnoreCaseAndIdNot(name, coffeeId);
		if (duplicate) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Coffee name already exists");
		}
	}
}