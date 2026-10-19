package io.github.tandapnoelbansikah.coffeeshop.service.orders;

import io.github.tandapnoelbansikah.coffeeshop.domain.CoffeeOrder;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** Calls Payments with a service token and validates its response against the persisted coffee order. */
public class PaymentCheckoutClient {
    private final OAuth2AuthorizedClientManager tokens;
    private final RestClient client;

    public PaymentCheckoutClient(OAuth2AuthorizedClientManager tokens, RestClient client) {
        this.tokens = tokens;
        this.client = client;
    }

    public Checkout start(CoffeeOrder order) {
        if (order.getStatus() != io.github.tandapnoelbansikah.coffeeshop.domain.OrderStatus.AWAITING_PAYMENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only unpaid orders can start checkout");
        }
        String principal = "coffee-shop-backend";
        var authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId("payment-service")
                .principal(principal).build();
        org.springframework.security.oauth2.client.OAuth2AuthorizedClient authorized;
        try {
            authorized = tokens.authorize(authorizeRequest);
        } catch (OAuth2AuthorizationException unavailable) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Payment service identity is unavailable", unavailable);
        }
        if (authorized == null || authorized.getAccessToken() == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Payment service identity is unavailable");
        }
        String orderReference = order.getId().toString();
        long amountMinor;
        try {
            amountMinor = order.getTotalPrice().movePointRight(2).longValueExact();
        } catch (ArithmeticException invalidCurrencyPrecision) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Order total is not an exact USD-cent amount");
        }
        try {
            Checkout checkout = client.post().uri("/internal/payments")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorized.getAccessToken().getTokenValue())
                    .header("Idempotency-Key", "coffee-order-" + order.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CreatePayment(orderReference, amountMinor, "USD"))
                    .retrieve().body(Checkout.class);
            if (checkout == null || !orderReference.equals(checkout.orderReference())
                    || amountMinor != checkout.amountMinor() || !"USD".equals(checkout.currency())
                    || !"PENDING".equals(checkout.status()) || checkout.id() == null
                    || !checkout.id().matches("[0-9a-fA-F-]{36}")
                    || checkout.checkoutUrl() == null || !checkout.checkoutUrl().startsWith("https://checkout.stripe.com/")) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment service returned an invalid checkout response");
            }
            UUID.fromString(checkout.id());
            return checkout;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RestClientException exception) {
            // Keep the order pending. Reusing the order-derived key recovers a response lost after Payment reserved it.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Payment could not start yet; retry checkout from your order history", exception);
        }
    }

    private record CreatePayment(String orderReference, long amountMinor, String currency) { }

    public record Checkout(String id, String orderReference, long amountMinor, String currency, String status, String checkoutUrl) { }
}