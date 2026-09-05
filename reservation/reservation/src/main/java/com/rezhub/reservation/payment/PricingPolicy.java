package com.rezhub.reservation.payment;

import java.time.Duration;

/**
 * Price per slot, Rez's commission cut, and the commitment window (how long before a slot's start
 * cancellation stops being free) — configured per facility, with an optional per-resource override.
 *
 * @param priceCents         price per slot, in the smallest currency unit (e.g. cents)
 * @param currency           ISO 4217 currency code, lowercase (e.g. "eur"), matching Stripe's convention
 * @param commissionFraction Rez's cut of the price, e.g. 0.10 for 10%
 * @param commitmentWindow   how long before a slot's start the commitment cutoff falls; also bounds
 *                            how long a Stripe hold can stay open (FR-011)
 */
public record PricingPolicy(long priceCents, String currency, double commissionFraction, Duration commitmentWindow) {

    /**
     * Stripe guarantees authorizations are capturable for up to ~7 days (shorter on some card
     * networks). This cap stays comfortably under that so a hold's lifetime (commitment cutoff to
     * resolution point) never risks an unpredictable authorization expiry (FR-011).
     */
    private static final Duration MAX_COMMITMENT_WINDOW = Duration.ofDays(5);

    /**
     * Rejects a configuration that would let a hold sit open longer than is safe under Stripe's
     * authorization capture limit, rather than accepting it and risking a silent failure later at the
     * commitment cutoff.
     */
    public void validate() {
        if (priceCents <= 0) {
            throw new IllegalArgumentException("priceCents must be positive");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        if (commissionFraction < 0.0 || commissionFraction > 1.0) {
            throw new IllegalArgumentException("commissionFraction must be between 0 and 1");
        }
        if (commitmentWindow == null || commitmentWindow.isZero() || commitmentWindow.isNegative()) {
            throw new IllegalArgumentException("commitmentWindow must be positive");
        }
        if (commitmentWindow.compareTo(MAX_COMMITMENT_WINDOW) > 0) {
            throw new IllegalArgumentException(
                "commitmentWindow must not exceed " + MAX_COMMITMENT_WINDOW
                    + " to stay safely under Stripe's authorization capture limit");
        }
    }
}
