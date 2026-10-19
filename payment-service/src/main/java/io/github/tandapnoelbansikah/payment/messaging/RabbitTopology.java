package io.github.tandapnoelbansikah.payment.messaging;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Declares durable result, delayed-retry, and dead-letter routes for the standalone demo. */
@Configuration
public class RabbitTopology {
    public static final String EVENTS = "payments.events";
    public static final String QUEUE = "demo.orders.payments";
    public static final String RETRY = "payments.retry";
    public static final String RETRY_KEY = "demo.orders.retry";
    public static final String DEAD = "payments.dead";
    public static final String DEAD_QUEUE = "demo.orders.dead";

    @Bean
    Declarables paymentRoutes() {
        var events = new TopicExchange(EVENTS, true, false);
        var retries = new DirectExchange(RETRY, true, false);
        var dead = new DirectExchange(DEAD, true, false);
        var queue = QueueBuilder.durable(QUEUE).deadLetterExchange(DEAD).deadLetterRoutingKey(DEAD_QUEUE).build();
        var retryQueue = QueueBuilder.durable(RETRY_KEY).ttl(3000).deadLetterExchange(EVENTS)
                .deadLetterRoutingKey("payment.retry").build();
        var deadQueue = QueueBuilder.durable(DEAD_QUEUE).build();
        return new Declarables(events, retries, dead, queue, retryQueue, deadQueue,
                BindingBuilder.bind(queue).to(events).with("payment.*"),
                BindingBuilder.bind(retryQueue).to(retries).with(RETRY_KEY),
                BindingBuilder.bind(deadQueue).to(dead).with(DEAD_QUEUE));
    }
}