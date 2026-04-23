package com.rezhub.reservation.orchestration;

import java.util.List;
import java.util.Map;

/**
 * Result of an availability check — which time slots are free for a given scope.
 */
public record AvailabilityResult(
    String scopeId,
    List<String> availableSlots,
    Map<String, String> attributes
) {}
