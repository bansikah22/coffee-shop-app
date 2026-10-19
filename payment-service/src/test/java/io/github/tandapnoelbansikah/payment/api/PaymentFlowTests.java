package io.github.tandapnoelbansikah.payment.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.github.tandapnoelbansikah.payment.application.CheckoutService;
import io.github.tandapnoelbansikah.payment.application.PaymentVerifier;
import io.github.tandapnoelbansikah.payment.domain.PaymentAttempt;
import io.github.tandapnoelbansikah.payment.provider.PaymentGateway;
import io.github.tandapnoelbansikah.payment.messaging.ConfirmedPublisher;
import io.github.tandapnoelbansikah.payment.messaging.OutboxPublisher;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentFlowTests {
    @Autowired MockMvc mvc;
    @Autowired CheckoutService checkout;
    @Autowired PaymentVerifier verifier;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PaymentGateway gateway;
    @MockitoSpyBean ConfirmedPublisher publisher;
    @Autowired OutboxPublisher outbox;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM payment_outbox");
        jdbc.update("DELETE FROM webhook_inbox");
        jdbc.update("DELETE FROM payment_attempts");
        when(gateway.create(any())).thenReturn(new PaymentGateway.Checkout("cs_test_example", "https://checkout.stripe.com/c/pay/test"));
    }

    @Test
    void rejectsAnonymousAndWrongAuthorizedParty() throws Exception {
        mvc.perform(post("/internal/payments").header("Idempotency-Key", "anonymous")
            .contentType(MediaType.APPLICATION_JSON).content(request()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/payments").header("Idempotency-Key", "wrong-client")
            .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_other")))
                .contentType(MediaType.APPLICATION_JSON).content(request())).andExpect(status().isForbidden());
    }

    @Test
    void createsAndReadsOnlyOwnAttempt() throws Exception {
        mvc.perform(post("/internal/payments").with(jwt().jwt(token -> token.subject("demo")
                .claim("azp", "payment-service").audience(java.util.List.of("payment-service")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_payments-workload")))
                .header("Idempotency-Key", "request-1").contentType(MediaType.APPLICATION_JSON).content(request()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amountMinor").value(300))
                .andExpect(jsonPath("$.status").value("PENDING"));
        var attempt = checkout.create("demo", "request-1", "order-1", 300, "USD");
        mvc.perform(get("/internal/payments/" + attempt.id()).with(jwt().jwt(token -> token.subject("someone-else")
            .claim("azp", "payment-service").audience(java.util.List.of("payment-service")))
                .authorities(new SimpleGrantedAuthority("SCOPE_payments-workload")))).andExpect(status().isNotFound());
        verify(gateway, times(1)).create(any());
    }

    @Test
    void rejectsChangedIdempotencyPayloadAndSecondKeyForSameOrder() {
        checkout.create("demo", "request-1", "order-1", 300, "USD");
        assertThatThrownBy(() -> checkout.create("demo", "request-1", "order-1", 400, "USD")).hasMessageContaining("409");
        assertThatThrownBy(() -> checkout.create("demo", "request-2", "order-1", 300, "USD")).hasMessageContaining("409");
        verify(gateway, times(1)).create(any());
    }

    @Test
    void retriesTimedOutCreationUsingSameDurableAttempt() {
        when(gateway.create(any())).thenThrow(new IllegalStateException("timeout"))
                .thenReturn(new PaymentGateway.Checkout("cs_test_example", "https://checkout.stripe.com/test"));
        assertThatThrownBy(() -> checkout.create("demo", "retry-1", "order-1", 300, "USD")).isInstanceOf(IllegalStateException.class);
        String reservedId = jdbc.queryForObject("SELECT id FROM payment_attempts", String.class);
        assertThat(checkout.create("demo", "retry-1", "order-1", 300, "USD").id()).isEqualTo(reservedId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_attempts", Integer.class)).isEqualTo(1);
    }

    @Test
    void verifiesPaidSnapshotAndEmitsOnlyOneOutboxEvent() {
        PaymentAttempt attempt = checkout.create("demo", "request-1", "order-1", 300, "USD");
        when(gateway.retrieve(attempt.sessionId())).thenReturn(snapshot(attempt, 300, false, "paid", "complete"));
        verifier.verify(attempt.sessionId());
        verifier.verify(attempt.sessionId());
        assertThat(checkout.find(attempt.id(), "demo").status()).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_outbox", Integer.class)).isEqualTo(1);
        when(gateway.retrieve(attempt.sessionId())).thenReturn(snapshot(attempt, 300, false, "unpaid", "expired"));
        verifier.verify(attempt.sessionId());
        assertThat(checkout.find(attempt.id(), "demo").status()).isEqualTo("SUCCEEDED");
    }

    @Test
    void rejectsMismatchAndLiveSnapshotWithoutOutbox() {
        var attempt = checkout.create("demo", "request-1", "order-1", 300, "USD");
        when(gateway.retrieve(attempt.sessionId())).thenReturn(snapshot(attempt, 301, false, "paid", "complete"));
        assertThatThrownBy(() -> verifier.verify(attempt.sessionId())).isInstanceOf(PaymentVerifier.VerificationMismatch.class);
        when(gateway.retrieve(attempt.sessionId())).thenReturn(snapshot(attempt, 300, true, "paid", "complete"));
        assertThatThrownBy(() -> verifier.verify(attempt.sessionId())).isInstanceOf(PaymentVerifier.VerificationMismatch.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_outbox", Integer.class)).isZero();
    }

    @Test
    void openUnpaidSessionRemainsPendingAndExpiryIsTerminal() {
        var attempt = checkout.create("demo", "request-1", "order-1", 300, "USD");
        when(gateway.retrieve(attempt.sessionId())).thenReturn(snapshot(attempt, 300, false, "unpaid", "open"));
        verifier.verify(attempt.sessionId());
        assertThat(checkout.find(attempt.id(), "demo").status()).isEqualTo("PENDING");
        when(gateway.retrieve(attempt.sessionId())).thenReturn(snapshot(attempt, 300, false, "unpaid", "expired"));
        verifier.verify(attempt.sessionId());
        assertThat(checkout.find(attempt.id(), "demo").status()).isEqualTo("EXPIRED");
    }

    @Test
    void acceptsSignedWebhookOnceAndRejectsInvalidStaleAndLiveEvents() throws Exception {
        String payload = "{\"id\":\"evt_example\",\"object\":\"event\",\"type\":\"checkout.session.completed\",\"livemode\":false,\"data\":{\"object\":{\"id\":\"cs_test_example\",\"object\":\"checkout.session\"}}}";
        long now = Instant.now().getEpochSecond();
        for (int delivery = 0; delivery < 2; delivery++) {
            mvc.perform(post("/webhooks/stripe").header("Stripe-Signature", signature(payload, now))
                    .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isNoContent());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook_inbox", Integer.class)).isEqualTo(1);
        mvc.perform(post("/webhooks/stripe").header("Stripe-Signature", "t=1,v1=invalid")
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isBadRequest());
        mvc.perform(post("/webhooks/stripe").header("Stripe-Signature", signature(payload, now - 600))
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isBadRequest());
        String live = payload.replace("false", "true");
        mvc.perform(post("/webhooks/stripe").header("Stripe-Signature", signature(live, now))
                .contentType(MediaType.APPLICATION_JSON).content(live)).andExpect(status().isBadRequest());
        mvc.perform(post("/webhooks/stripe").header("Stripe-Signature", signature(payload, now))
            .contentType(MediaType.APPLICATION_JSON).content(payload + " ")).andExpect(status().isBadRequest());
    }

        @Test
        void retainsOutboxAcrossPublicationFailureAndPublishesSameEventOnRecovery() {
        var attempt = checkout.create("demo", "request-1", "order-1", 300, "USD");
        when(gateway.retrieve(attempt.sessionId())).thenReturn(snapshot(attempt, 300, false, "paid", "complete"));
        verifier.verify(attempt.sessionId());
        doThrow(new IllegalStateException("broker unavailable")).when(publisher).send(anyString(), anyString(), any());
        outbox.publishBatch();
        assertThat(jdbc.queryForObject("SELECT published FROM payment_outbox", Boolean.class)).isFalse();
        String eventId = jdbc.queryForObject("SELECT event_id FROM payment_outbox", String.class);
        doNothing().when(publisher).send(anyString(), anyString(), any());
        outbox.publishBatch();
        assertThat(jdbc.queryForObject("SELECT published FROM payment_outbox WHERE event_id=?", Boolean.class, eventId)).isTrue();
        assertThat(checkout.find(attempt.id(), "demo").status()).isEqualTo("SUCCEEDED");
        }

        @Test
        void validatesActualJwtSignatureAudienceAndExpiry() throws Exception {
        var attempt = checkout.create("demo", "request-1", "order-1", 300, "USD");
        String path = "/internal/payments/" + attempt.id();
        mvc.perform(get(path).header("Authorization", "Bearer " + token("payment-service", Instant.now().plusSeconds(300).getEpochSecond())))
            .andExpect(status().isOk());
        mvc.perform(get(path).header("Authorization", "Bearer " + token("wrong-audience", Instant.now().plusSeconds(300).getEpochSecond())))
            .andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", "Bearer " + token(null, Instant.now().plusSeconds(300).getEpochSecond())))
            .andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", "Bearer " + token("payment-service", Instant.now().plusSeconds(300).getEpochSecond()) + "tampered"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", "Bearer " + token("payment-service", Instant.now().minusSeconds(300).getEpochSecond())))
            .andExpect(status().isUnauthorized());
        }

        @Test
        void grantsWorkloadAuthorityOnlyToDedicatedAuthorizedPartyWithPaymentAudience() throws Exception {
        var attempt = checkout.create("demo", "request-1", "order-1", 300, "USD");
        mvc.perform(get("/internal/payments/" + attempt.id())
            .header("Authorization", "Bearer " + workloadToken("payment-service", "payment-service")))
            .andExpect(status().isNotFound());
        mvc.perform(get("/internal/payments/" + attempt.id())
            .header("Authorization", "Bearer " + workloadToken("coffee-shop", "payment-service")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/internal/payments/" + attempt.id())
            .header("Authorization", "Bearer " + workloadToken("payment-service", "other-api")))
            .andExpect(status().isUnauthorized());
        }

        private String token(String audience, long expires) throws Exception {
        var encoder = java.util.Base64.getUrlEncoder().withoutPadding();
        var claims = new java.util.LinkedHashMap<String, Object>();
        claims.put("iss", "payment-poc");
        claims.put("sub", "demo");
        if ("payment-service".equals(audience)) claims.put("azp", "payment-service");
        claims.put("exp", expires);
        if (audience != null) claims.put("aud", audience);
        String unsigned = encoder.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8)) + "."
            + encoder.encodeToString(new com.google.gson.Gson().toJson(claims).getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-only-shared-secret-at-least-32-bytes".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return unsigned + "." + encoder.encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
        }

    private String workloadToken(String authorizedParty, String audience) throws Exception {
        var encoder = java.util.Base64.getUrlEncoder().withoutPadding();
        long now = Instant.now().getEpochSecond();
        var claims = new java.util.LinkedHashMap<String, Object>();
        claims.put("iss", "payment-poc");
        claims.put("sub", "keycloak-workload-id");
        claims.put("aud", audience);
        claims.put("azp", authorizedParty);
        claims.put("iat", now);
        claims.put("exp", now + 300);
        String unsigned = encoder.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(new com.google.gson.Gson().toJson(claims).getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-only-shared-secret-at-least-32-bytes".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return unsigned + "." + encoder.encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
    }

    private PaymentGateway.Snapshot snapshot(PaymentAttempt attempt, long amount, boolean live, String paid, String status) {
        return new PaymentGateway.Snapshot(attempt.sessionId(), attempt.id(), attempt.orderReference(), amount, "usd", live, paid, status);
    }

    private String signature(String payload, long timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("whsec_test_only_not_a_real_secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
    }

    private String request() { return "{\"orderReference\":\"order-1\",\"amountMinor\":300,\"currency\":\"USD\"}"; }
}