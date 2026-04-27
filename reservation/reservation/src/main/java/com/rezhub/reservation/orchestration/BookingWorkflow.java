package com.rezhub.reservation.orchestration;

/**
 * Domain-specific booking workflow. One implementation per booking domain (courts, suppliers, …).
 * Responsible for resolving candidate resources and delegating to ReservationGateway.
 */
public interface BookingWorkflow {
    String domain();

    AvailabilityResult checkAvailability(
        OriginRequestContext origin,
        BookingContext context,
        BookingIntent intent);

    ReservationHandle book(
        OriginRequestContext origin,
        BookingContext context,
        BookingIntent intent);

    void cancel(
        OriginRequestContext origin,
        BookingContext context,
        CancelIntent intent);
}
