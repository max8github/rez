package com.rezhub.reservation.dto;

/**
 * @deprecated Legacy wire type used only by the /selection endpoint (RezAction) and the
 * Telegram/BookingService path. New callers use {@code BookingEndpoint} which accepts a
 * flat {@code Set<String> resourceIds} directly. Remove once the legacy path is retired.
 */
@Deprecated
public record SelectionItem(String id, EntityType type) {}
