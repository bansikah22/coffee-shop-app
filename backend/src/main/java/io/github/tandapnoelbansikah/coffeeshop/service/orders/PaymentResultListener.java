package io.github.tandapnoelbansikah.coffeeshop.service.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import io.github.tandapnoelbansikah.coffeeshop.configuration.PaymentMessagingConfiguration;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Validates result identity and acknowledges only after the order transaction commits. */
@Component
@ConditionalOnProperty(name = "app.payment.enabled", havingValue = "true")
public class PaymentResultListener {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentResultListener.class);
    private final ObjectMapper json = new ObjectMapper();
    private final PaymentResultProcessor processor;
    private final RabbitTemplate rabbit;

    public PaymentResultListener(PaymentResultProcessor processor, RabbitTemplate rabbit) {
        this.processor = processor;
        this.rabbit = rabbit;
    }

    @RabbitListener(queues = PaymentMessagingConfiguration.QUEUE, ackMode = "MANUAL")
    public void receive(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        PaymentResultEvent event;
        try {
            if (message.getBody().length > 16384) throw new IllegalArgumentException("Oversized payment event");
            event = json.readValue(message.getBody(), PaymentResultEvent.class);
            if (!event.eventId().equals(message.getMessageProperties().getMessageId())) {
                throw new IllegalArgumentException("Payment event ID does not match message ID");
            }
        } catch (IOException | RuntimeException invalid) {
            channel.basicReject(tag, false);
            LOG.warn("Invalid coffee order payment event dead-lettered");
            return;
        }
        try {
            processor.apply(event);
        } catch (IllegalArgumentException conflict) {
            channel.basicReject(tag, false);
            LOG.warn("Conflicting coffee order payment event {} dead-lettered", event.eventId());
            return;
        } catch (RuntimeException transientFailure) {
            Object header = message.getMessageProperties().getHeaders().get("x-coffee-retries");
            int retries = header instanceof Number count ? count.intValue() : 0;
            if (retries >= 3) {
                channel.basicReject(tag, false);
            } else {
                var retry = MessageBuilder.fromClonedMessage(message).setHeader("x-coffee-retries", retries + 1).build();
                rabbit.send(PaymentMessagingConfiguration.RETRY, PaymentMessagingConfiguration.RETRY_KEY, retry);
                channel.basicAck(tag, false);
            }
            return;
        }
        channel.basicAck(tag, false);
    }
}