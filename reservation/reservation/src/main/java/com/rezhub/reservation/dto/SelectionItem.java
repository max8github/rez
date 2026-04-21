package com.rezhub.reservation.dto;

/** Internal wire type carrying a resource ID and its entity type through the booking fan-out. */
public record SelectionItem(String id, EntityType type) {}
