package io.github.tandapnoelbansikah.payment.application;

import io.github.tandapnoelbansikah.payment.persistence.PaymentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Processes durable webhook receipts and periodically reconciles unresolved provider sessions. */
@Component
@ConditionalOnProperty(name = "app.role", havingValue = "payments", matchIfMissing = true)
public class PaymentWorkers {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentWorkers.class);
    private final JdbcTemplate jdbc;
    private final PaymentVerifier verifier;
    private final PaymentStore store;
    private final boolean enabled;

    public PaymentWorkers(JdbcTemplate jdbc, PaymentVerifier verifier, PaymentStore store,
            @Value("${app.workers-enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.verifier = verifier;
        this.store = store;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.worker-delay:10000}")
    public void inbox() {
        if (!enabled) return;
        var receipts = jdbc.queryForList("SELECT event_id,session_id FROM webhook_inbox WHERE state='PENDING' ORDER BY received_at LIMIT 20");
        for (var receipt : receipts) {
            String eventId = (String) receipt.get("event_id");
            try {
                verifier.verify((String) receipt.get("session_id"));
                jdbc.update("UPDATE webhook_inbox SET state='PROCESSED' WHERE event_id=?", eventId);
            } catch (PaymentVerifier.VerificationMismatch mismatch) {
                jdbc.update("UPDATE webhook_inbox SET state='QUARANTINED' WHERE event_id=?", eventId);
                LOG.warn("Quarantined payment webhook {} due to provider snapshot mismatch", eventId);
            } catch (RuntimeException unavailable) {
                LOG.warn("Payment webhook {} remains pending; verification will retry", eventId);
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.reconcile-delay:60000}")
    public void reconcile() {
        if (!enabled) return;
        for (var attempt : store.pending()) {
            if (attempt.sessionId() == null) continue;
            try {
                verifier.verify(attempt.sessionId());
            } catch (RuntimeException unavailable) {
                LOG.warn("Payment {} remains unresolved; reconciliation requires retry or review", attempt.id());
            }
        }
    }
}