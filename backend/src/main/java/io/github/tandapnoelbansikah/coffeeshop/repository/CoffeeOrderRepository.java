package io.github.tandapnoelbansikah.coffeeshop.repository;

import io.github.tandapnoelbansikah.coffeeshop.domain.CoffeeOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** Provides persistence operations for submitted orders. Introduced in Git on 2026-10-13. */
public interface CoffeeOrderRepository extends JpaRepository<CoffeeOrder, Long> {

	List<CoffeeOrder> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

	List<CoffeeOrder> findAllByOrderByCreatedAtDesc();
}