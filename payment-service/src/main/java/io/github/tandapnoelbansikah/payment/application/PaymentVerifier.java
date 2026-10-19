package io.github.tandapnoelbansikah.payment.application;

import com.google.gson.Gson;
import io.github.tandapnoelbansikah.payment.domain.PaymentAttempt;
import io.github.tandapnoelbansikah.payment.domain.PaymentResult;
import io.github.tandapnoelbansikah.payment.persistence.PaymentStore;
import io.github.tandapnoelbansikah.payment.provider.PaymentGateway;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Verifies provider truth before atomically recording state and its publishable result. */
@Service
@ConditionalOnProperty(name = "app.role", havingValue = "payments", matchIfMissing = true)
public class PaymentVerifier {
    private final PaymentStore store;
    private final PaymentGateway gateway;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Gson json = new Gson();

    public PaymentVerifier(PaymentStore store, PaymentGateway gateway, JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.store = store;
        this.gateway = gateway;
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    public void verify(String sessionId) {
        PaymentAttempt attempt = store.bySession(sessionId).orElse(null);
        // A webhook can precede session-reference persistence. Leave it pending for the next worker pass.
        if (attempt == null) throw new IllegalStateException("Session not attached yet");
        var snapshot = gateway.retrieve(sessionId);
        if (snapshot.live() || !sessionId.equals(snapshot.sessionId()) || !attempt.id().equals(snapshot.paymentId())
                || !attempt.orderReference().equals(snapshot.orderReference()) || snapshot.amountMinor() != attempt.amountMinor()
                || !"usd".equals(snapshot.currency())) {
            throw new VerificationMismatch();
        }
        String status = "paid".equals(snapshot.paymentStatus()) && "complete".equals(snapshot.sessionStatus()) ? "SUCCEEDED"
                : "expired".equals(snapshot.sessionStatus()) && "unpaid".equals(snapshot.paymentStatus()) ? "EXPIRED" : null;
        if (status == null) return;
        transaction.executeWithoutResult(ignored -> {
            int changed = jdbc.update("UPDATE payment_attempts SET status=? WHERE id=? AND status='PENDING'", status, attempt.id());
            if (changed == 0) return;
            String eventId = UUID.randomUUID().toString();
            var result = new PaymentResult(1, eventId, "payment." + status.toLowerCase(java.util.Locale.ROOT),
                    attempt.id(), attempt.orderReference(), attempt.amountMinor(), attempt.currency(), status);
            // The state update and outbox insert roll back together if either statement fails.
            jdbc.update("INSERT INTO payment_outbox(event_id,payment_id,payload) VALUES (?,?,?)", eventId, attempt.id(), json.toJson(result));
        });
    }

    public static class VerificationMismatch extends RuntimeException { }
}