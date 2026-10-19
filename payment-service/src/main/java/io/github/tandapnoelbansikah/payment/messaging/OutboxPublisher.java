package io.github.tandapnoelbansikah.payment.messaging;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Publishes saved events at least once; never marks published before a positive routed confirm. */
@Component
@ConditionalOnProperty(name = "app.role", havingValue = "payments", matchIfMissing = true)
public class OutboxPublisher {
    private static final Logger LOG = LoggerFactory.getLogger(OutboxPublisher.class);
    private final JdbcTemplate jdbc;
    private final ConfirmedPublisher publisher;
    private final boolean enabled;

    public OutboxPublisher(JdbcTemplate jdbc, ConfirmedPublisher publisher, @Value("${app.workers-enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.publisher = publisher;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.worker-delay:10000}")
    public void scheduled() { if (enabled) publishBatch(); }

    public void publishBatch() {
        var events = jdbc.queryForList("SELECT event_id,payload FROM payment_outbox WHERE published=FALSE ORDER BY created_at LIMIT 20");
        for (var event : events) {
            String id = (String) event.get("event_id");
            String payload = (String) event.get("payload");
            var message = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON).setMessageId(id)
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT).build();
            try {
                publisher.send(RabbitTopology.EVENTS, JsonParser.parseString(payload).getAsJsonObject().get("eventType").getAsString(), message);
                // A crash after confirm but before this update republishes the same ID. Consumers must deduplicate.
                jdbc.update("UPDATE payment_outbox SET published=TRUE WHERE event_id=?", id);
            } catch (RuntimeException unavailable) {
                LOG.warn("Outbox event {} remains pending; publication will retry", id);
                break;
            }
        }
    }
}