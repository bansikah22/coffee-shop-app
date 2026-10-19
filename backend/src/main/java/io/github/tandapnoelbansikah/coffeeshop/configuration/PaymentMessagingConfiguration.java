package io.github.tandapnoelbansikah.coffeeshop.configuration;

import org.springframework.amqp.core.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Gives the real Orders subscriber its own queue and bounded retry/dead-letter path. */
@Configuration
@ConditionalOnProperty(name = "app.payment.enabled", havingValue = "true")
public class PaymentMessagingConfiguration {
    public static final String EVENTS = "payments.events";
    public static final String QUEUE = "coffee.orders.payments";
    public static final String RETRY = "coffee.orders.retry.exchange";
    public static final String RETRY_KEY = "coffee.orders.retry";
    public static final String RETRY_QUEUE = "coffee.orders.retry.queue";
    public static final String DEAD = "coffee.orders.dead.exchange";
    public static final String DEAD_KEY = "coffee.orders.dead";
    public static final String DEAD_QUEUE = "coffee.orders.dead.queue";

    @Bean
    Declarables orderPaymentRoutes() {
        var events = new TopicExchange(EVENTS, true, false);
        var retry = new DirectExchange(RETRY, true, false);
        var dead = new DirectExchange(DEAD, true, false);
        var queue = QueueBuilder.durable(QUEUE).deadLetterExchange(DEAD).deadLetterRoutingKey(DEAD_KEY).build();
        var retryQueue = QueueBuilder.durable(RETRY_QUEUE).ttl(3000).deadLetterExchange(EVENTS)
                .deadLetterRoutingKey("payment.retry").build();
        var deadQueue = QueueBuilder.durable(DEAD_QUEUE).build();
        return new Declarables(events, retry, dead, queue, retryQueue, deadQueue,
                BindingBuilder.bind(queue).to(events).with("payment.*"),
                BindingBuilder.bind(retryQueue).to(retry).with(RETRY_KEY),
                BindingBuilder.bind(deadQueue).to(dead).with(DEAD_KEY));
    }
}