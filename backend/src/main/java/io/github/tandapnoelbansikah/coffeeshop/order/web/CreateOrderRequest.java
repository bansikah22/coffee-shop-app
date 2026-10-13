package io.github.tandapnoelbansikah.coffeeshop.order.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

record CreateOrderRequest(@NotNull Long coffeeId, @Min(1) int quantity) {
}