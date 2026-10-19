package io.github.tandapnoelbansikah.payment.api;

import io.github.tandapnoelbansikah.payment.application.CheckoutService;
import io.github.tandapnoelbansikah.payment.domain.PaymentAttempt;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Internal demo API; the caller's JWT subject scopes all payment reads and request keys. */
@RestController
@Validated
@ConditionalOnProperty(name = "app.role", havingValue = "payments", matchIfMissing = true)
public class PaymentController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentController.class);
    private final CheckoutService service;

    public PaymentController(CheckoutService service) { this.service = service; }

    @PostMapping("/internal/payments")
    PaymentView create(@AuthenticationPrincipal Jwt caller,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9_-]{1,100}") String key,
            @Valid @RequestBody CreatePayment request) {
        requireOrderService(caller);
        return PaymentView.from(service.create(caller.getSubject(), key, request.orderReference(), request.amountMinor(), request.currency()));
    }

    @GetMapping("/internal/payments/{id}")
    PaymentView find(@AuthenticationPrincipal Jwt caller, @PathVariable String id) {
        requireOrderService(caller);
        return PaymentView.from(service.find(id, caller.getSubject()));
    }

    private void requireOrderService(Jwt caller) {
        if (caller == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "A workload token is required");
        }
        boolean authorizedPartyMatches = "payment-service".equals(caller.getClaimAsString("azp"));
        boolean audienceMatches = caller.getAudience() != null && caller.getAudience().contains("payment-service");
        if (!authorizedPartyMatches || !audienceMatches) {
            LOGGER.warn("Rejected internal caller: authorizedPartyMatches={}, audienceMatches={}",
                    authorizedPartyMatches, audienceMatches);
            throw new AccessDeniedException("Only the authenticated coffee order service may call Payments");
        }
    }

    @GetMapping(value = "/checkout/return", produces = "text/plain")
    String returned() {
        return "Sandbox checkout closed. This page does not confirm payment. Read payment status through the authenticated API.";
    }

    public record CreatePayment(@jakarta.validation.constraints.NotNull @Pattern(regexp = "[A-Za-z0-9_-]{1,100}") String orderReference,
            @Min(50) @Max(100000) long amountMinor,
            @jakarta.validation.constraints.NotNull @Pattern(regexp = "USD") String currency) { }

    public record PaymentView(String id, String orderReference, long amountMinor, String currency, String status, String checkoutUrl) {
        static PaymentView from(PaymentAttempt payment) {
            return new PaymentView(payment.id(), payment.orderReference(), payment.amountMinor(), payment.currency(),
                    payment.status(), payment.checkoutUrl());
        }
    }
}