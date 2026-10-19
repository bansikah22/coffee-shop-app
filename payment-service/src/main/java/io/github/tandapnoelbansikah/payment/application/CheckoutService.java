package io.github.tandapnoelbansikah.payment.application;

import io.github.tandapnoelbansikah.payment.domain.PaymentAttempt;
import io.github.tandapnoelbansikah.payment.domain.UsdAmount;
import io.github.tandapnoelbansikah.payment.persistence.PaymentStore;
import io.github.tandapnoelbansikah.payment.provider.PaymentGateway;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Reserves idempotent attempts before external calls without holding an SQL transaction open. */
@Service
@ConditionalOnProperty(name = "app.role", havingValue = "payments", matchIfMissing = true)
public class CheckoutService {
    private final PaymentStore store;
    private final PaymentGateway gateway;

    public CheckoutService(PaymentStore store, PaymentGateway gateway) {
        this.store = store;
        this.gateway = gateway;
    }

    public PaymentAttempt create(String owner, String key, String order, long amount, String currency) {
        new UsdAmount(amount, currency);
        PaymentAttempt attempt = store.byRequest(owner, key).orElse(null);
        if (attempt == null) {
            try {
                attempt = store.reserve(owner, key, order, amount, currency);
            } catch (DuplicateKeyException conflict) {
                attempt = store.byRequest(owner, key).orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.CONFLICT, "An attempt already exists for this order"));
            }
        }
        if (!attempt.orderReference().equals(order) || attempt.amountMinor() != amount || !attempt.currency().equals(currency)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key already used for a different request");
        }
        if (attempt.sessionId() != null) return attempt;
        // Stripe can prune old keys. An unresolved old attempt needs review, never a blind new charge.
        if (Duration.between(attempt.createdAt(), Instant.now()).toHours() >= 23) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unresolved attempt requires reconciliation");
        }
        var checkout = gateway.create(attempt);
        return store.attachSession(attempt, checkout.sessionId(), checkout.url());
    }

    public PaymentAttempt find(String id, String owner) {
        return store.byId(id, owner).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}