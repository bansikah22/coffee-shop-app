package io.github.tandapnoelbansikah.payment.messaging;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** Waits for both broker acceptance and mandatory-routing checks before reporting publication. */
@Component
public class ConfirmedPublisher {
    private final RabbitTemplate rabbit;

    public ConfirmedPublisher(RabbitTemplate rabbit) { this.rabbit = rabbit; }

    public void send(String exchange, String routingKey, Message message) {
        var correlation = new CorrelationData(UUID.randomUUID().toString());
        try {
            rabbit.send(exchange, routingKey, message, correlation);
            var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            // An exchange can confirm a message that reached no queue. Mandatory returns close that gap.
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw new IllegalStateException("Broker did not accept and route the message");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publication interrupted", interrupted);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException unavailable) {
            throw new IllegalStateException("Publication unconfirmed; safe to retry the same event", unavailable);
        }
    }
}