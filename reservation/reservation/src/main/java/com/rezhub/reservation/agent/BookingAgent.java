package com.rezhub.reservation.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import com.rezhub.reservation.orchestration.BookingApplicationService;
import com.rezhub.reservation.orchestration.OriginRequestContext;
import com.rezhub.reservation.orchestration.ReservationGatewayAkka;

/**
 * Conversational booking agent for Rez.
 *
 * Handles natural-language court booking requests from players via any messaging surface.
 * Maintains per-session conversation history automatically via Akka's Agent session mechanism.
 *
 * Session ID is supplied by the caller (conversationId in OriginRequestContext), scoped
 * per user per facility so concurrent users don't share conversation state.
 */
@Component(id = "booking-agent")
public class BookingAgent extends Agent {

    private final BookingApplicationService bookingService;
    private final ReservationGatewayAkka reservationGateway;
    private final ComponentClient componentClient;

    public BookingAgent(BookingApplicationService bookingService,
                        ReservationGatewayAkka reservationGateway,
                        ComponentClient componentClient) {
        this.bookingService = bookingService;
        this.reservationGateway = reservationGateway;
        this.componentClient = componentClient;
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
        - Always reply in the same language the member used in their most recent message.
          If the language cannot be determined, default to English.
        - Be concise and friendly. Members are on their phones.
        - This chat renders HTML, not Markdown. Never format a link as `[text](url)` — it will show
          up as literal brackets. If a tool response already contains an `<a href="...">...</a>` tag,
          include that tag in your reply byte-for-byte — do not paraphrase, shorten, or rewrite it into
          a bare URL or Markdown. If a tool response instead gives you a bare URL with no `<a>` tag,
          put that URL directly in your reply text as-is (the chat client auto-links it); never invent
          your own HTML tags around a bare URL.
        - If a member asks to book and has already given a clear date/time and players,
          call bookCourt directly. Do not ask them to choose a court unless they explicitly
          say they care which court it is.
        - Use checkAvailability when the member explicitly asks what is free, or when
          the booking request is underspecified and you need nearby options to continue.
        - If the member expresses the date or time in natural language (e.g. "today", "tomorrow",
          "next Tuesday", "heute", "morgen", "übermorgen", "oggi", "domani", a weekday name,
          or equivalents in other languages), first call resolveDateTime.
        - Never invent ISO dates yourself when the member used natural language. Use resolveDateTime first.
        - If the user message contains a [resolvedDateTime:...] prefix, treat that as the authoritative
          resolved local date/time for the member request and use it exactly.
        - Confirm missing or ambiguous date, time, or players before calling bookCourt.
        - For bookCourt, use the sender's display name for the person making the request.
          If a partner is mentioned by name (e.g. "with John"), use that name as-is.
        - If no courts are free at the requested time, suggest the nearest available slot.
        - When bookCourt is called, always pass the recipientId exactly as it appears in the [recipient:X] prefix of the message.
        - When bookCourt returns a value starting with `BOOKING_SUBMITTED:`, the booking is NOT yet confirmed. Reply with exactly `__SILENT_BOOKING_ACK__` and nothing else. The booking outcome will be delivered asynchronously in a separate notification. Do NOT say the court is booked or wish them a good game.
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
     * Single command handler: receives a user message in context (origin + raw text).
     * The LLM will use bookingTools to check availability, book, or cancel.
     */
    public Effect<String> chat(AgentRequest request) {
        OriginRequestContext origin = request.origin();
        String facilityId = origin.attributes().getOrDefault("facilityId", "");
        String timezone = origin.attributes().getOrDefault("timezone", "Europe/Berlin");
        String senderName = origin.senderDisplayName() != null && !origin.senderDisplayName().isBlank()
            ? origin.senderDisplayName() : "Player";
        String recipientId = origin.recipientId();

        String systemMsg = SYSTEM_MESSAGE.formatted(
            java.time.LocalDate.now(java.time.ZoneId.of(BookingTools.safeZoneId(timezone).getId()))
                .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", java.util.Locale.ENGLISH)));

        java.util.Optional<java.time.LocalDateTime> resolvedDateTime =
            BookingTools.resolveNaturalDateTime(
                request.message(),
                BookingTools.safeZoneId(timezone),
                java.time.ZonedDateTime.now(BookingTools.safeZoneId(timezone)));

        String resolvedPrefix = resolvedDateTime
            .map(dt -> " [resolvedDateTime:" + dt.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "]")
            .orElse("");

        BookingTools bookingTools = new BookingTools(bookingService, reservationGateway, componentClient, origin);

        return effects()
            .systemMessage(systemMsg)
            .tools(bookingTools)
            .userMessage("[facility:" + facilityId + "] [recipient:" + recipientId + "]" + resolvedPrefix + " "
                + senderName + ": " + request.message())
            .thenReply();
    }

    /** Input record: the caller's resolved origin context plus the raw user message. */
    public record AgentRequest(OriginRequestContext origin, String message) {}

    /**
     * Legacy record kept for backward compatibility with existing test callers.
     * @deprecated Use AgentRequest with OriginRequestContext instead.
     */
    @Deprecated
    public record BookingRequest(String facilityId, String senderName, String recipientId, String timezone, String message) {}
}
