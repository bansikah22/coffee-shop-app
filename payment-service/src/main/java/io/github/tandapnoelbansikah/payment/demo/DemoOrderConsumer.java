package io.github.tandapnoelbansikah.payment.demo;

import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import io.github.tandapnoelbansikah.payment.domain.PaymentResult;
import io.github.tandapnoelbansikah.payment.messaging.ConfirmedPublisher;
import io.github.tandapnoelbansikah.payment.messaging.RabbitTopology;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Commits before acknowledging; poison messages dead-letter while transient failures retry with delay. */
@Component
@ConditionalOnProperty(name = "app.role", havingValue = "consumer")
public class DemoOrderConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(DemoOrderConsumer.class);
    private final DemoOrderStore store;
    private final ConfirmedPublisher publisher;

    public DemoOrderConsumer(DemoOrderStore store, ConfirmedPublisher publisher) {
        this.store = store;
        this.publisher = publisher;
    }

    @RabbitListener(queues = RabbitTopology.QUEUE)
    public void receive(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        PaymentResult event;
        try {
            if (message.getBody().length > 16384) throw new IllegalArgumentException("Oversized event");
            event = new Gson().fromJson(new String(message.getBody(), StandardCharsets.UTF_8), PaymentResult.class);
            if (event == null || !event.eventId().equals(message.getMessageProperties().getMessageId())) {
                throw new IllegalArgumentException("Invalid event identity");
            }
        } catch (RuntimeException invalid) {
            channel.basicReject(tag, false);
            LOG.warn("Malformed payment event sent to dead-letter queue");
            return;
        }
        try {
            store.accept(event);
        } catch (IllegalArgumentException conflict) {
            channel.basicReject(tag, false);
            LOG.warn("Conflicting payment event {} requires dead-letter review", event.eventId());
            return;
        } catch (RuntimeException transientFailure) {
            Object header = message.getMessageProperties().getHeaders().get("x-demo-retries");
            int retries = header instanceof Number count ? count.intValue() : 0;
            if (retries >= 3) {
                channel.basicReject(tag, false);
            } else {
                var retry = MessageBuilder.fromClonedMessage(message).setHeader("x-demo-retries", retries + 1).build();
                // Confirm the delayed copy before acknowledging the original; a crash may duplicate, never silently drop it.
                publisher.send(RabbitTopology.RETRY, RabbitTopology.RETRY_KEY, retry);
                channel.basicAck(tag, false);
            }
            return;
        }
        // This acknowledgment is deliberately outside the database transaction and after its successful commit.
        channel.basicAck(tag, false);
        LOG.info("Applied demo payment event {} for order {}", event.eventId(), event.orderReference());
    }
}