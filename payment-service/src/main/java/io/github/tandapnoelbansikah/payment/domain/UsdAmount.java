package io.github.tandapnoelbansikah.payment.domain;

/** Validates exact USD cents with a deliberately small sandbox charge limit. */
public record UsdAmount(long amountMinor, String currency) {
    public UsdAmount {
        if (!"USD".equals(currency) || amountMinor < 50 || amountMinor > 100_000) {
            throw new IllegalArgumentException("Use USD integer cents between 50 and 100000");
        }
    }
}