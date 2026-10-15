package io.github.tandapnoelbansikah.coffeeshop.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Validated request payload for placing an order. Introduced in Git on 2026-10-13. */
record CreateOrderRequest(@NotNull Long coffeeId, @Min(1) int quantity) {
}