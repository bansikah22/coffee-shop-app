package io.github.tandapnoelbansikah.payment.provider.stripe;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import io.github.tandapnoelbansikah.payment.domain.PaymentAttempt;
import io.github.tandapnoelbansikah.payment.provider.PaymentGateway;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Contains official SDK types and rejects live-mode objects before they affect local state. */
@Component
@ConditionalOnProperty(name = "app.role", havingValue = "payments", matchIfMissing = true)
public class StripeGateway implements PaymentGateway {
    private final StripeClient client;
    private final String publicUrl;
        private final String successUrl;
        private final String cancelUrl;

        public StripeGateway(@Value("${app.stripe-key}") String key, @Value("${app.public-url}") String publicUrl,
            @Value("${app.success-url}") String successUrl, @Value("${app.cancel-url}") String cancelUrl) {
        if (key == null || !key.matches("(?:sk_test_|rk_test_|rkcs_test_)[A-Za-z0-9]+")) {
            throw new IllegalStateException("Use a Stripe sandbox secret key: sk_test_, rk_test_, or rkcs_test_");
        }
        URI destination = URI.create(publicUrl);
        if (destination.getHost() == null || !(destination.getScheme().equals("https")
                || destination.getScheme().equals("http") && destination.getHost().equals("localhost"))) {
            throw new IllegalStateException("PAYMENT_PUBLIC_URL must use HTTPS or localhost HTTP");
        }
        this.client = StripeClient.builder().setApiKey(key).setConnectTimeout(5000).setReadTimeout(15000).setMaxNetworkRetries(2).build();
        this.publicUrl = publicUrl.replaceAll("/$", "");
        this.successUrl = safeReturnUrl(successUrl);
        this.cancelUrl = safeReturnUrl(cancelUrl);
    }

    @Override
    public Checkout create(PaymentAttempt attempt) {
        var params = SessionCreateParams.builder().setMode(SessionCreateParams.Mode.PAYMENT)
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setClientReferenceId(attempt.id()).putMetadata("paymentId", attempt.id())
                .putMetadata("orderReference", attempt.orderReference())
                .setSuccessUrl(successUrl).setCancelUrl(cancelUrl)
                .addLineItem(SessionCreateParams.LineItem.builder().setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder().setCurrency("usd")
                                .setUnitAmount(attempt.amountMinor())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Coffee sandbox order").build()).build()).build()).build();
        try {
            Session session = client.v1().checkout().sessions().create(params,
                    RequestOptions.builder().setIdempotencyKey(attempt.id()).build());
            rejectLive(session);
            if (session.getUrl() == null || !session.getUrl().startsWith("https://checkout.stripe.com/")) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unexpected checkout destination");
            }
            return new Checkout(session.getId(), session.getUrl());
        } catch (StripeException exception) {
            // Retrying the same local attempt reuses Stripe's key; a timeout is not a failed payment.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe unavailable; retry with the same Idempotency-Key");
        }
    }

    @Override
    public Snapshot retrieve(String sessionId) {
        try {
            Session session = client.v1().checkout().sessions().retrieve(sessionId);
            rejectLive(session);
            return new Snapshot(session.getId(), session.getClientReferenceId(), session.getMetadata().get("orderReference"),
                    session.getAmountTotal(), session.getCurrency(), false, session.getPaymentStatus(), session.getStatus());
        } catch (StripeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe status verification unavailable");
        }
    }

    private void rejectLive(Session session) {
        if (!Boolean.FALSE.equals(session.getLivemode())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Expected sandbox Checkout Session");
        }
    }

    private String safeReturnUrl(String value) {
        URI destination = URI.create(value);
        if (destination.getHost() == null || !("https".equals(destination.getScheme())
                || "http".equals(destination.getScheme()) && destination.getHost().equals("localhost"))) {
            throw new IllegalStateException("Checkout return URLs must use HTTPS or localhost HTTP");
        }
        return value;
    }
}