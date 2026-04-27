package com.rezhub.reservation.orchestration;

/**
 * Maps an interaction-surface origin to a booking domain and scope.
 * Examples:
 *   Telegram bot token → bookingDomain="courts", scopeId=facilityId
 *   Mobile supplier section → bookingDomain="suppliers", scopeId=clubId
 */
public interface BookingContextResolver {
    BookingContext resolve(OriginRequestContext origin);
}
