package io.github.tandapnoelbansikah.coffeeshop.domain;

/** Tracks an order from placement through pickup or doorstep delivery. */
public enum OrderStatus {
	AWAITING_PAYMENT,
	PAID,
	PAYMENT_EXPIRED,
	READY,
	OUT_FOR_DELIVERY,
	COMPLETED
}