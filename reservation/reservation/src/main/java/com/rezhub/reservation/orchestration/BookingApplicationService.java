package com.rezhub.reservation.orchestration;

/**
 * Deterministic application-layer entry point for all booking operations.
 * Resolves BookingContext from origin, selects the correct BookingWorkflow, delegates.
 */
public interface BookingApplicationService {
    AvailabilityResult checkAvailability(OriginRequestContext origin, BookingIntent intent);
    ReservationHandle book(OriginRequestContext origin, BookingIntent intent);
    void cancel(OriginRequestContext origin, CancelIntent intent);
}
