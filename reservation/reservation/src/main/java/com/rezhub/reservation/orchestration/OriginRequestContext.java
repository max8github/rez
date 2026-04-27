package com.rezhub.reservation.orchestration;

import java.util.Map;

/**
 * Transport-agnostic description of who sent a booking request and from which interaction surface.
 * Built by the interaction-surface adapter (e.g. TelegramEndpoint) before any domain logic runs.
 */
public record OriginRequestContext(
    String origin,
    String senderExternalId,
    String senderDisplayName,
    String recipientId,
    String conversationId,
    Map<String, String> attributes
) {}
