package io.github.tandapnoelbansikah.payment.demo;

import com.google.gson.Gson;
import io.github.tandapnoelbansikah.payment.domain.PaymentResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Simulates future Orders using its own database; event receipt and order update commit together. */
@Service
@ConditionalOnProperty(name = "app.role", havingValue = "consumer")
public class DemoOrderStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public DemoOrderStore(JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    public void accept(PaymentResult event) {
        String fingerprint = fingerprint(new Gson().toJson(event));
        try {
            transaction.executeWithoutResult(ignored -> {
                jdbc.update("INSERT INTO processed_events(event_id,fingerprint) VALUES (?,?)", event.eventId(), fingerprint);
                var existing = jdbc.queryForList("SELECT * FROM demo_orders WHERE order_reference=? FOR UPDATE", event.orderReference());
                String status = "SUCCEEDED".equals(event.status()) ? "PREPARATION_ALLOWED" : "NOT_PAID";
                if (existing.isEmpty()) {
                    jdbc.update("INSERT INTO demo_orders(order_reference,payment_id,amount_minor,currency,status) VALUES (?,?,?,?,?)",
                            event.orderReference(), event.paymentId(), event.amountMinor(), event.currency(), status);
                } else {
                    var order = existing.getFirst();
                    if (!event.paymentId().equals(order.get("payment_id")) || event.amountMinor() != ((Number) order.get("amount_minor")).longValue()
                            || !event.currency().equals(order.get("currency"))) {
                        throw new IllegalArgumentException("Conflicting payment snapshot requires review");
                    }
                    if (!"PREPARATION_ALLOWED".equals(order.get("status"))) {
                        jdbc.update("UPDATE demo_orders SET status=?,updated_at=CURRENT_TIMESTAMP WHERE order_reference=?", status, event.orderReference());
                    }
                }
            });
        } catch (DuplicateKeyException duplicate) {
            // Catch outside the transaction: PostgreSQL aborts a transaction after a unique-key violation.
            var saved = jdbc.queryForList("SELECT fingerprint FROM processed_events WHERE event_id=?", event.eventId());
            if (saved.isEmpty()) throw duplicate;
            if (!fingerprint.equals(saved.getFirst().get("fingerprint"))) {
                throw new IllegalArgumentException("Event ID reused with a different payload");
            }
        }
    }

    private String fingerprint(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}