package com.rezhub.reservation.orchestration;

/**
 * Result of a {@code book()} attempt. Replaces the bare {@code ReservationHandle} return type once
 * the FR-005/FR-012 booking-time gates mean not every attempt actually submits a reservation.
 */
public sealed interface BookingHandle {
    record Booked(ReservationHandle handle) implements BookingHandle {}

    /** FR-005: no reservation was submitted — the player has no payment method on file yet. */
    record CardSetupRequired(String checkoutUrl) implements BookingHandle {}

    /** FR-012: no reservation was submitted — the facility has a PricingPolicy but incomplete Stripe onboarding. */
    record FacilityNotPayable() implements BookingHandle {}
}
