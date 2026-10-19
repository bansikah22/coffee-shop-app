package io.github.tandapnoelbansikah.coffeeshop.service.orders;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Versioned payment-service event; no provider or card data crosses the service boundary. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record PaymentResultEvent(int schemaVersion, String eventId, String eventType, String paymentId,
		String orderReference, long amountMinor, String currency, String status) {
}