package io.github.tandapnoelbansikah.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class UsdAmountTests {
    @Test
    void acceptsExactUsdCents() {
        assertThat(new UsdAmount(300, "USD").amountMinor()).isEqualTo(300);
    }

    @Test
    void rejectsOtherCurrenciesAndUnsafeAmounts() {
        assertThatThrownBy(() -> new UsdAmount(300, "XAF")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UsdAmount(49, "USD")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UsdAmount(100_001, "USD")).isInstanceOf(IllegalArgumentException.class);
    }
}