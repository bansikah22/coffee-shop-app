package io.github.tandapnoelbansikah.coffeeshop.repository;

import io.github.tandapnoelbansikah.coffeeshop.domain.CoffeeOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/** Provides persistence operations for submitted orders. Introduced in Git on 2026-10-13. */
public interface CoffeeOrderRepository extends JpaRepository<CoffeeOrder, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select coffeeOrder from CoffeeOrder coffeeOrder where coffeeOrder.id = :id")
	Optional<CoffeeOrder> findByIdForUpdate(@Param("id") Long id);

	List<CoffeeOrder> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

	List<CoffeeOrder> findAllByOrderByCreatedAtDesc();
}