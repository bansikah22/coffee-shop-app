package io.github.tandapnoelbansikah.coffeeshop.service.orders;

import io.github.tandapnoelbansikah.coffeeshop.domain.OrderStatus;
import io.github.tandapnoelbansikah.coffeeshop.repository.CoffeeOrderRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Applies result event and event-id receipt atomically; a duplicate delivery is a verified no-op. */
@Service
public class PaymentResultProcessor {
    private final TransactionTemplate transaction;
    private final JdbcTemplate jdbc;
    private final CoffeeOrderRepository orders;
        private final ApplicationEventPublisher events;

    public PaymentResultProcessor(TransactionTemplate transaction, JdbcTemplate jdbc,
            CoffeeOrderRepository orders, ApplicationEventPublisher events) {
        this.transaction = transaction;
        this.jdbc = jdbc;
        this.orders = orders;
        this.events = events;
    }

    public boolean apply(PaymentResultEvent event) {
        validate(event);
        String fingerprint = fingerprint(event);
        try {
            return Boolean.TRUE.equals(transaction.execute(ignored -> applyOnce(event, fingerprint)));
        } catch (DuplicateKeyException duplicate) {
            String original = jdbc.queryForList("SELECT payload_fingerprint FROM processed_payment_events WHERE event_id=?", event.eventId())
                    .stream().findFirst().map(row -> (String) row.get("payload_fingerprint")).orElse(null);
            if (!fingerprint.equals(original)) throw new IllegalArgumentException("Payment event ID was reused with a different payload");
            return false;
        }
    }

    private boolean applyOnce(PaymentResultEvent event, String fingerprint) {
        jdbc.update("INSERT INTO processed_payment_events(event_id,payload_fingerprint) VALUES (?,?)", event.eventId(), fingerprint);
        long orderId = Long.parseLong(event.orderReference());
        var order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new IllegalArgumentException("Payment references an unknown order"));
        boolean transitioned;
        if ("payment.succeeded".equals(event.eventType()) && "SUCCEEDED".equals(event.status())) {
            transitioned = order.markPaymentSucceeded(event.paymentId(), event.amountMinor(), event.currency());
        } else if ("payment.expired".equals(event.eventType()) && "EXPIRED".equals(event.status())) {
            transitioned = order.markPaymentExpired(event.paymentId(), event.amountMinor(), event.currency());
        } else {
            throw new IllegalArgumentException("Unsupported payment result state");
        }
        if (transitioned && order.getStatus() == OrderStatus.PAID) {
            events.publishEvent(OrderPlacedEvent.from(order));
            return true;
        }
        return false;
    }

    private void validate(PaymentResultEvent event) {
        if (event == null || event.schemaVersion() != 1 || event.eventId() == null || event.paymentId() == null
                || event.orderReference() == null || !event.orderReference().matches("[1-9][0-9]{0,17}")
                || !"USD".equals(event.currency()) || event.amountMinor() < 50 || event.amountMinor() > 100_000) {
            throw new IllegalArgumentException("Invalid payment event contract");
        }
        UUID.fromString(event.eventId());
        UUID.fromString(event.paymentId());
    }

    private String fingerprint(PaymentResultEvent event) {
        try {
            String canonical = String.join("|", Integer.toString(event.schemaVersion()), event.eventId(), event.eventType(),
                    event.paymentId(), event.orderReference(), Long.toString(event.amountMinor()), event.currency(), event.status());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}