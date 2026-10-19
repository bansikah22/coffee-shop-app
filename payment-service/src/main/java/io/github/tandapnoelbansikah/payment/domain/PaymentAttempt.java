package io.github.tandapnoelbansikah.payment.domain;

import java.time.Instant;

/** Immutable persisted view; payment state is independent of coffee fulfillment. */
public record PaymentAttempt(String id, String ownerId, String requestKey, String orderReference,
        long amountMinor, String currency, String status, String sessionId, String checkoutUrl, Instant createdAt) {
}