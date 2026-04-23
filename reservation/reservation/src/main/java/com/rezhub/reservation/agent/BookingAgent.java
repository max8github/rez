package com.rezhub.reservation.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

/**
 * Conversational booking agent for Rez.
 *
 * Handles natural-language court booking requests from players via Matrix (or any MS).
 * Maintains per-session conversation history automatically via Akka's Agent session mechanism.
 *
 * Session ID should be scoped per user per facility, e.g. "roomId:senderUserId",
 * so concurrent users don't share conversation state.
 */
@Component(id = "booking-agent")
public class BookingAgent extends Agent {

    private final BookingTools bookingTools;

    public BookingAgent(BookingTools bookingTools) {
        this.bookingTools = bookingTools;
    }

    private static final String SYSTEM_MESSAGE = """
        You are Rez, a friendly and efficient court booking assistant for a sports club.

        Your job is to help club members book and cancel tennis courts through a chat interface.

        ## What you can do
        - Check court availability at a given date and time
        - Book a court for one or more players
        - Cancel an existing reservation by its ID
        - Look up the details of an existing reservation by its ID

        ## How to behave
        - Always reply in the same language the member used.
        - Be concise and friendly. Members are on their phones.
        - If a member asks to book but doesn't specify a time precisely, use checkAvailability
          to find nearby free slots and ask them to confirm before booking.
        - If the member expresses the date or time in natural language, first call resolveDateTime.
          This includes phrases like today, tomorrow, next Tuesday, oggi, domani, dopodomani,
          lunedi, martedi, mercoledi, giovedi, venerdi, sabato, domenica.
        - Never invent ISO dates yourself when the member used natural language. Use resolveDateTime first.
        - If the user message contains a [resolvedDateTime:...] prefix, treat that as the authoritative
          resolved local date/time for the member request and use it exactly.
        - Always confirm the date, time, and players before calling bookCourt.
        - For bookCourt, use the sender's display name for the person making the request.
          If a partner is mentioned by name (e.g. "with John"), use that name as-is.
        - If no courts are free at the requested time, suggest the nearest available slot.
        - When bookCourt is called, always pass the recipientId exactly as it appears in the [recipient:X] prefix of the message.
        - When bookCourt returns, the booking is NOT yet confirmed — availability is checked asynchronously. Reply only with "Request submitted — you'll receive a notification shortly with the outcome." Do NOT say the court is booked or wish them a good game.
        - When cancelReservation succeeds, reply with a brief confirmation.
        - NEVER cancel an existing reservation unless the member explicitly uses a word like "cancel", "delete", "remove", or equivalent in their language. If the member repeats a booking request for a time slot already booked in this session, treat it as a request for a SECOND court, not a replacement.
        - Date/times passed to tools must be in ISO-8601 format: YYYY-MM-DDTHH:MM:SS
        - Today is %s. Use this to resolve relative days like "Thursday" or "next Tuesday" to exact dates.

        ## What you cannot do
        - Handle payments or subscriptions
        - Add or remove members from the club
        - Change court opening hours or club policies

        If asked about something outside your scope, politely say so and redirect to the club admin.
        """;

    /**
     * Single command handler: receives a user message in context (facilityId + sender + text).
     * The LLM will use bookingService tools to check availability, book, or cancel.
     */
    public Effect<String> chat(BookingRequest request) {
        String tz = request.timezone() != null ? request.timezone() : "Europe/Berlin";
        String systemMsg = SYSTEM_MESSAGE.formatted(
            java.time.LocalDate.now(java.time.ZoneId.of(tz))
                .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", java.util.Locale.ENGLISH)));
        java.util.Optional<java.time.LocalDateTime> resolvedDateTime =
            BookingTools.resolveNaturalDateTime(
                request.message(),
                BookingTools.safeZoneId(tz),
                java.time.ZonedDateTime.now(BookingTools.safeZoneId(tz)));

        String resolvedPrefix = resolvedDateTime
            .map(dateTime -> " [resolvedDateTime:" + dateTime.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "]")
            .orElse("");

        return effects()
            .systemMessage(systemMsg)
            .tools(bookingTools)
            .userMessage("[facility:" + request.facilityId() + "] [recipient:" + request.recipientId() + "]" + resolvedPrefix + " "
                + request.senderName() + ": " + request.message())
            .thenReply();
    }

    /**
     * Incoming message from the messaging service.
     *
     * @param facilityId  Akka entity ID of the facility (maps to the MS room/channel)
     * @param senderName  display name of the sender, for personalisation
     * @param timezone    IANA timezone ID for the facility, e.g. "Europe/Berlin"
     * @param message     raw natural-language text from the player
     */
    public record BookingRequest(String facilityId, String senderName, String recipientId, String timezone, String message) {}
}
