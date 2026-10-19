package io.github.tandapnoelbansikah.payment.provider;

import io.github.tandapnoelbansikah.payment.domain.PaymentAttempt;

/** Provider-neutral Checkout contract used by orchestration and reconciliation. */
public interface PaymentGateway {
    Checkout create(PaymentAttempt attempt);
    Snapshot retrieve(String sessionId);

    record Checkout(String sessionId, String url) { }
    record Snapshot(String sessionId, String paymentId, String orderReference, long amountMinor,
            String currency, boolean live, String paymentStatus, String sessionStatus) { }
}