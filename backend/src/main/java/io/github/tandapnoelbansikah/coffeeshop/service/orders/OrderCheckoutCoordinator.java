package io.github.tandapnoelbansikah.coffeeshop.service.orders;

import io.github.tandapnoelbansikah.coffeeshop.domain.CoffeeOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Starts payment outside the order-creation transaction and keeps retries tied to the same order. */
@Service
public class OrderCheckoutCoordinator {
    private final OrderService orders;
    private final ObjectProvider<PaymentCheckoutClient> paymentClients;
    private final boolean enabled;

    public OrderCheckoutCoordinator(OrderService orders, ObjectProvider<PaymentCheckoutClient> paymentClients,
            @Value("${app.payment.enabled:false}") boolean enabled) {
        this.orders = orders;
        this.paymentClients = paymentClients;
        this.enabled = enabled;
    }

    public Checkout start(CoffeeOrder order) {
        requireEnabled();
        PaymentCheckoutClient client = paymentClients.getIfAvailable();
        var checkout = client.start(order);
        orders.attachPaymentAttempt(order.getId(), order.getCustomerId(), checkout.id());
        return new Checkout(checkout.id(), checkout.checkoutUrl());
    }

    public void requireEnabled() {
        if (!enabled || paymentClients.getIfAvailable() == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Payments are required but not enabled");
        }
    }

    public record Checkout(String paymentId, String checkoutUrl) { }
}