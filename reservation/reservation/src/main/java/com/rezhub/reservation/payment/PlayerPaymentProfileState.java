package com.rezhub.reservation.payment;

import java.util.Optional;

/**
 * Minimal mapping from a player's canonical {@code identity} {@code userId} to a Stripe
 * {@code customerId} and default {@code paymentMethodId}. Does not itself decide whether that
 * {@code userId} is linked to a Hit account — it simply resolves whatever Stripe customer that
 * {@code userId} currently maps to.
 */
public record PlayerPaymentProfileState(
    String userId,
    Optional<String> stripeCustomerId,
    Optional<String> defaultPaymentMethodId
) {

    public static PlayerPaymentProfileState empty(String userId) {
        return new PlayerPaymentProfileState(userId, Optional.empty(), Optional.empty());
    }

    public boolean hasPaymentMethod() {
        return stripeCustomerId.isPresent() && defaultPaymentMethodId.isPresent();
    }

    public PlayerPaymentProfileState withStripeCustomerId(String stripeCustomerId) {
        return new PlayerPaymentProfileState(userId, Optional.of(stripeCustomerId), defaultPaymentMethodId);
    }

    public PlayerPaymentProfileState withDefaultPaymentMethodId(String defaultPaymentMethodId) {
        return new PlayerPaymentProfileState(userId, stripeCustomerId, Optional.of(defaultPaymentMethodId));
    }
}
