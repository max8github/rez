package com.rezhub.reservation.orchestration;

import java.util.Map;

/**
 * Resolved booking domain context for a request.
 * Produced by BookingContextResolver from an OriginRequestContext.
 *
 * scopeId is intentionally generic:
 *   - court booking: facilityId
 *   - supplier booking: supplier pool id, club id, or organisation id
 */
public record BookingContext(
    String bookingDomain,
    String scopeId,
    String timezone,
    Map<String, String> attributes
) {}
