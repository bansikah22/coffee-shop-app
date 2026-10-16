package io.github.tandapnoelbansikah.coffeeshop.web.catalog;

import java.util.List;

import io.github.tandapnoelbansikah.coffeeshop.service.catalog.CoffeeCatalogService;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the coffee catalog HTTP endpoints. Introduced in Git on 2026-10-13. */
@RestController
@RequestMapping("/api/v1/coffees")
class CoffeeCatalogController {

	private final CoffeeCatalogService coffeeCatalogService;

	CoffeeCatalogController(CoffeeCatalogService coffeeCatalogService) {
		this.coffeeCatalogService = coffeeCatalogService;
	}

	@GetMapping
	List<CoffeeResponse> listCoffees() {
		return coffeeCatalogService.listCoffees().stream().map(CoffeeResponse::from).toList();
	}

	@GetMapping("/{coffeeId}")
	CoffeeResponse getCoffee(@PathVariable Long coffeeId) {
		return CoffeeResponse.from(coffeeCatalogService.findCoffee(coffeeId));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	CoffeeResponse createCoffee(@Valid @RequestBody CoffeeRequest request) {
		return CoffeeResponse.from(coffeeCatalogService.createCoffee(request.name(), request.description(), request.price(),
				request.available()));
	}

	@PutMapping("/{coffeeId}")
	CoffeeResponse updateCoffee(@PathVariable Long coffeeId, @Valid @RequestBody CoffeeRequest request) {
		return CoffeeResponse.from(coffeeCatalogService.updateCoffee(coffeeId, request.name(), request.description(),
				request.price(), request.available()));
	}

	@DeleteMapping("/{coffeeId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteCoffee(@PathVariable Long coffeeId) {
		coffeeCatalogService.deleteCoffee(coffeeId);
	}
}