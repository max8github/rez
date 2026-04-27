package com.rezhub.reservation.orchestration;

/** Resolves the set of candidate court resources for a booking context. */
public interface CourtDirectory {
    CourtBookingScope resolveScope(BookingContext context);
}
