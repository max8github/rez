package com.rezhub.reservation.orchestration;

import java.util.Map;
import java.util.Optional;

/**
 * Transport-agnostic description of who sent a booking request and from which interaction surface.
 * Built by the interaction-surface adapter (e.g. TelegramEndpoint) before any domain logic runs.
 *
 * {@code identityUserId} is the shared `identity` service's resolved userId for this sender, when
 * available. It is populated only for origins that resolve identity (currently just Telegram) and is
 * always empty when resolution failed or wasn't attempted — see spec 001-telegram-identity-resolution.
 */
public record OriginRequestContext(
    String origin,
    String senderExternalId,
    String senderDisplayName,
    String recipientId,
    String conversationId,
    Map<String, String> attributes,
    Optional<String> identityUserId
) {}
