package io.github.tandapnoelbansikah.payment.persistence;

import io.github.tandapnoelbansikah.payment.domain.PaymentAttempt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Owns durable attempt reservation; uniqueness prevents duplicate demo-order charges. */
@Repository
@ConditionalOnProperty(name = "app.role", havingValue = "payments", matchIfMissing = true)
public class PaymentStore {
    private final JdbcTemplate jdbc;
    private final RowMapper<PaymentAttempt> mapper = (row, index) -> new PaymentAttempt(row.getString("id"),
            row.getString("owner_id"), row.getString("request_key"), row.getString("order_reference"),
            row.getLong("amount_minor"), row.getString("currency"), row.getString("status"),
            row.getString("session_id"), row.getString("checkout_url"), row.getTimestamp("created_at").toInstant());

    public PaymentStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PaymentAttempt> byRequest(String owner, String key) {
        return jdbc.query("SELECT * FROM payment_attempts WHERE owner_id=? AND request_key=?", mapper, owner, key).stream().findFirst();
    }

    public Optional<PaymentAttempt> byId(String id, String owner) {
        return jdbc.query("SELECT * FROM payment_attempts WHERE id=? AND owner_id=?", mapper, id, owner).stream().findFirst();
    }

    public Optional<PaymentAttempt> bySession(String sessionId) {
        return jdbc.query("SELECT * FROM payment_attempts WHERE session_id=?", mapper, sessionId).stream().findFirst();
    }

    @Transactional
    public PaymentAttempt reserve(String owner, String key, String order, long amount, String currency) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO payment_attempts(id,owner_id,request_key,order_reference,amount_minor,currency,status) VALUES (?,?,?,?,?,?,'PENDING')",
                id, owner, key, order, amount, currency);
        return byId(id, owner).orElseThrow();
    }

    public PaymentAttempt attachSession(PaymentAttempt attempt, String sessionId, String url) {
        jdbc.update("UPDATE payment_attempts SET session_id=?, checkout_url=? WHERE id=? AND session_id IS NULL", sessionId, url, attempt.id());
        return byId(attempt.id(), attempt.ownerId()).orElseThrow();
    }

    public List<PaymentAttempt> pending() {
        return jdbc.query("SELECT * FROM payment_attempts WHERE status='PENDING' ORDER BY created_at LIMIT 20", mapper);
    }
}