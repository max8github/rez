package com.rezhub.reservation.payment;

import akka.javasdk.client.ComponentClient;
import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.customer.facility.FacilityState;
import com.rezhub.reservation.infrastructure.StripeService;

import java.util.Optional;

/**
 * Shared booking-time payability checks (FR-005, FR-012), split into two independent methods rather
 * than one combined check because {@code BookingEndpoint} can only ever call one of them — it has no
 * player-identity concept at all (research.md #10).
 */
public class PaymentGate {

    private final ComponentClient componentClient;
    private final StripeService stripeService;

    public PaymentGate(ComponentClient componentClient, StripeService stripeService) {
        this.componentClient = componentClient;
        this.stripeService = stripeService;
    }

    /**
     * FR-005: true if the resolved player identity already has a saved payment method on file. An
     * absent identity (no resolved {@code userId} at all) is never payable — there is nothing to check
     * against.
     */
    public boolean isPlayerPayable(Optional<String> identityUserId) {
        if (identityUserId.isEmpty()) {
            return false;
        }
        PlayerPaymentProfileState profile = componentClient
            .forKeyValueEntity(identityUserId.get())
            .method(PlayerPaymentProfileEntity::getProfile)
            .invoke();
        return profile.hasPaymentMethod();
    }

    /**
     * FR-012: true if the facility has a {@code PricingPolicy} configured and a Stripe connected
     * account that Stripe confirms is charges-enabled. A facility with no {@code PricingPolicy} at all
     * is free-to-book (payments simply don't apply to it) and is therefore also considered payable —
     * this gate only blocks the specific case of "configured to charge, but not able to collect."
     */
    public boolean isFacilityPayable(String facilityId) {
        FacilityState state = componentClient
            .forEventSourcedEntity(facilityId)
            .method(FacilityEntity::getState)
            .invoke();
        if (state.pricingPolicy().isEmpty()) {
            return true;
        }
        return state.stripeConnectedAccountId()
            .map(stripeService::isConnectAccountChargesEnabled)
            .orElse(false);
    }

    /**
     * True if the facility has a {@code PricingPolicy} configured at all — i.e. session fees actually
     * apply here. A facility with none is free-to-book, so there is nothing for a player to ever be
     * charged and no reason to demand a payment method on file before booking.
     */
    public boolean facilityRequiresPayment(String facilityId) {
        FacilityState state = componentClient
            .forEventSourcedEntity(facilityId)
            .method(FacilityEntity::getState)
            .invoke();
        return state.pricingPolicy().isPresent();
    }
}
