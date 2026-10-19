package io.github.tandapnoelbansikah.payment.api;

import com.google.gson.JsonParser;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Accepts only signed sandbox Checkout events and persists identifiers before acknowledging. */
@RestController
@ConditionalOnProperty(name = "app.role", havingValue = "payments", matchIfMissing = true)
public class StripeWebhookController {
    private final JdbcTemplate jdbc;
    private final String secret;

    public StripeWebhookController(JdbcTemplate jdbc, @Value("${app.webhook-secret}") String secret) {
        if (!secret.startsWith("whsec_") || secret.length() < 16) {
            throw new IllegalStateException("Set STRIPE_WEBHOOK_SECRET to the sandbox endpoint or CLI signing secret");
        }
        this.jdbc = jdbc;
        this.secret = secret;
    }

    @PostMapping(value = "/webhooks/stripe", consumes = "application/json")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void receive(@RequestBody byte[] raw, @RequestHeader("Stripe-Signature") String signature) {
        if (raw.length > 65536) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        String payload = new String(raw, StandardCharsets.UTF_8);
        try {
            Webhook.constructEvent(payload, signature, secret);
        } catch (SignatureVerificationException | RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook signature or payload");
        }
        try {
            var event = JsonParser.parseString(payload).getAsJsonObject();
            if (!event.has("livemode") || !event.get("livemode").isJsonPrimitive()
                    || !event.get("livemode").getAsJsonPrimitive().isBoolean() || event.get("livemode").getAsBoolean()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sandbox events only");
            }
            String type = event.get("type").getAsString();
            if (!("checkout.session.completed".equals(type) || "checkout.session.expired".equals(type))) return;
            var session = event.getAsJsonObject("data").getAsJsonObject("object");
            String eventId = event.get("id").getAsString();
            String sessionId = session.get("id").getAsString();
            if (!eventId.matches("evt_[A-Za-z0-9_]{1,240}") || !sessionId.matches("cs_test_[A-Za-z0-9_]{1,230}")) {
                throw new IllegalArgumentException("Invalid identifiers");
            }
            jdbc.update("INSERT INTO webhook_inbox(event_id,session_id) VALUES (?,?)", eventId, sessionId);
        } catch (DuplicateKeyException duplicate) {
            // Stripe retries delivery; the durable primary key makes repeat receipt a successful no-op.
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook fields");
        }
    }
}