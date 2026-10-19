package io.github.tandapnoelbansikah.payment.domain;

import java.util.UUID;

/** Versioned, minimal event contract; contains no card data, credentials, or payer details. */
public record PaymentResult(int schemaVersion, String eventId, String eventType, String paymentId,
        String orderReference, long amountMinor, String currency, String status) {
    public PaymentResult {
        if (schemaVersion != 1 || eventId == null || paymentId == null || orderReference == null
                || !orderReference.matches("[A-Za-z0-9_-]{1,100}")
                || !("SUCCEEDED".equals(status) && "payment.succeeded".equals(eventType)
                || "EXPIRED".equals(status) && "payment.expired".equals(eventType))) {
            throw new IllegalArgumentException("Invalid payment event");
        }
        UUID.fromString(eventId);
        UUID.fromString(paymentId);
        new UsdAmount(amountMinor, currency);
    }
}