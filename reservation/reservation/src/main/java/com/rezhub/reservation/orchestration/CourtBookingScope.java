package com.rezhub.reservation.orchestration;

import java.util.Map;
import java.util.Set;

/** Resolved scope for a court-booking operation: facility, timezone, candidate resources. */
public record CourtBookingScope(
    String facilityId,
    String timezone,
    Set<String> resourceIds,
    Map<String, String> attributes
) {}
