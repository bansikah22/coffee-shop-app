package io.github.tandapnoelbansikah.payment.provider.stripe;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

/** Tests key-family guardrails with synthetic values; constructors make no Stripe API calls. */
class StripeGatewayTests {
    @Test
    void acceptsSandboxSecretKeyFamiliesWithoutCallingStripe() {
        for (String prefix : new String[] { "sk_test_", "rk_test_", "rkcs_test_" }) {
                assertThatCode(() -> new StripeGateway(prefix + "synthetic123", "http://localhost:18081",
                    "http://localhost:8088/orders?payment=return", "http://localhost:8088/orders?payment=cancel"))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsLivePublishableEmptyAndMalformedKeys() {
        for (String key : new String[] { "", "sk_test_", "rk_test_", "rkcs_test_", "sk_live_synthetic123",
                "rk_live_synthetic123", "rkcs_live_synthetic123", "pk_test_synthetic123", "whsec_synthetic123",
                "rkcs_test_synthetic123\n", " rkcs_test_synthetic123", "rkcs_test_bad-key" }) {
                assertThatThrownBy(() -> new StripeGateway(key, "http://localhost:18081",
                    "http://localhost:8088/orders?payment=return", "http://localhost:8088/orders?payment=cancel"))
                    .isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(() -> new StripeGateway(null, "http://localhost:18081",
            "http://localhost:8088/orders?payment=return", "http://localhost:8088/orders?payment=cancel"))
                .isInstanceOf(IllegalStateException.class);
    }
}