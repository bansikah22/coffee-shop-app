package io.github.tandapnoelbansikah.coffeeshop.catalog.repository;

import java.util.List;

import io.github.tandapnoelbansikah.coffeeshop.catalog.domain.Coffee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoffeeRepository extends JpaRepository<Coffee, Long> {

	List<Coffee> findAllByOrderByNameAsc();

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}