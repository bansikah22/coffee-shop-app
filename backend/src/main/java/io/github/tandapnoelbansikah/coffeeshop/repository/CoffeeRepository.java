package io.github.tandapnoelbansikah.coffeeshop.repository;

import java.util.List;

import io.github.tandapnoelbansikah.coffeeshop.domain.Coffee;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides persistence queries for catalog items. Introduced in Git on 2026-10-13. */
public interface CoffeeRepository extends JpaRepository<Coffee, Long> {

	List<Coffee> findAllByOrderByNameAsc();

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}