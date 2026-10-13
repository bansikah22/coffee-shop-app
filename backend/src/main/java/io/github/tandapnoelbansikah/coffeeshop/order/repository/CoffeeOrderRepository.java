package io.github.tandapnoelbansikah.coffeeshop.order.repository;

import io.github.tandapnoelbansikah.coffeeshop.order.domain.CoffeeOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoffeeOrderRepository extends JpaRepository<CoffeeOrder, Long> {
}