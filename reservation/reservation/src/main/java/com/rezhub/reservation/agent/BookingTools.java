package com.rezhub.reservation.agent;

import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;
import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.orchestration.AvailabilityResult;
import com.rezhub.reservation.orchestration.BookingApplicationServiceImpl;
import com.rezhub.reservation.orchestration.BookingIntent;
import com.rezhub.reservation.orchestration.CancelIntent;
import com.rezhub.reservation.orchestration.OriginRequestContext;
import com.rezhub.reservation.orchestration.ReservationDetails;
import com.rezhub.reservation.orchestration.ReservationGatewayAkka;
import com.rezhub.reservation.orchestration.ReservationHandle;
import com.rezhub.reservation.resource.ResourceV;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent-facing @FunctionTool surface for booking operations.
 * Delegates domain logic to BookingApplicationServiceImpl; keeps only
 * parameter validation, LLM-specific string formatting, and utility helpers.
 */
public class BookingTools {

    private static final Logger log = LoggerFactory.getLogger(BookingTools.class);
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Pattern TIME_PATTERN = Pattern.compile(
        "\\b(?:alle\\s*)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b",
        Pattern.CASE_INSENSITIVE);
    private static final Map<String, DayOfWeek> WEEKDAYS = Map.ofEntries(
        Map.entry("monday", DayOfWeek.MONDAY),
        Map.entry("mon", DayOfWeek.MONDAY),
        Map.entry("lunedi", DayOfWeek.MONDAY),
        Map.entry("lunedi'", DayOfWeek.MONDAY),
        Map.entry("martedi", DayOfWeek.TUESDAY),
        Map.entry("martedi'", DayOfWeek.TUESDAY),
        Map.entry("tuesday", DayOfWeek.TUESDAY),
        Map.entry("tue", DayOfWeek.TUESDAY),
        Map.entry("wednesday", DayOfWeek.WEDNESDAY),
        Map.entry("wed", DayOfWeek.WEDNESDAY),
        Map.entry("mercoledi", DayOfWeek.WEDNESDAY),
        Map.entry("mercoledi'", DayOfWeek.WEDNESDAY),
        Map.entry("thursday", DayOfWeek.THURSDAY),
        Map.entry("thu", DayOfWeek.THURSDAY),
        Map.entry("giovedi", DayOfWeek.THURSDAY),
        Map.entry("giovedi'", DayOfWeek.THURSDAY),
        Map.entry("friday", DayOfWeek.FRIDAY),
        Map.entry("fri", DayOfWeek.FRIDAY),
        Map.entry("venerdi", DayOfWeek.FRIDAY),
        Map.entry("venerdi'", DayOfWeek.FRIDAY),
        Map.entry("saturday", DayOfWeek.SATURDAY),
        Map.entry("sat", DayOfWeek.SATURDAY),
        Map.entry("sabato", DayOfWeek.SATURDAY),
        Map.entry("sunday", DayOfWeek.SUNDAY),
        Map.entry("sun", DayOfWeek.SUNDAY),
        Map.entry("domenica", DayOfWeek.SUNDAY)
    );

    private final BookingApplicationServiceImpl bookingService;
    private final ReservationGatewayAkka reservationGateway;
    private final ComponentClient componentClient;

    public BookingTools(BookingApplicationServiceImpl bookingService,
                        ReservationGatewayAkka reservationGateway,
                        ComponentClient componentClient) {
        this.bookingService = bookingService;
        this.reservationGateway = reservationGateway;
        this.componentClient = componentClient;
    }

    @FunctionTool(description = """
        Check court availability at a facility for a specific date and time.
        Returns a list of available courts or states that none are free.
        Call this before booking to know what slots exist.
        """)
    public String checkAvailability(String facilityId, String dateTimeIso) {
        log.info("checkAvailability: facilityId={}, dateTime={}", facilityId, dateTimeIso);
        LocalDateTime requestedTime;
        try {
            requestedTime = LocalDateTime.parse(dateTimeIso, ISO_FMT);
        } catch (DateTimeParseException e) {
            return "Invalid date/time format. Please use ISO-8601, e.g. 2026-03-20T11:00:00";
        }

        String pastValidation = validateNotInPast(facilityId, requestedTime);
        if (pastValidation != null) {
            return pastValidation;
        }

        OriginRequestContext origin = directOrigin("", facilityId);
        BookingIntent intent = new BookingIntent(
            BookingIntent.BookingAction.CHECK_AVAILABILITY,
            requestedTime, null, List.of(), List.of(), null, Map.of());

        AvailabilityResult result = bookingService.checkAvailability(origin, intent);

        String noRoomsMsg = result.attributes().get("message");
        if (noRoomsMsg != null) return noRoomsMsg;

        if (result.availableSlots().isEmpty()) {
            String alternatives = result.attributes().getOrDefault("alternatives", "");
            return "No courts available at " + dateTimeIso + ". " + alternatives;
        }

        return "Available courts at " + dateTimeIso + ": " + String.join(", ", result.availableSlots());
    }

    @FunctionTool(description = """
        Book a court at a facility for specific players at a specific date and time.
        Use this for direct booking requests when the member has already specified
        a clear date/time and players. Do not call checkAvailability first unless the
        member explicitly asks which courts are available or wants alternatives.
        playerNames must be a comma-separated list of the players' display names (e.g. "Max,John").
        Use the sender's name for the person making the request.
        recipientId is the notification recipient identifier found in the [recipient:X] message prefix — always pass it unchanged.
        Returns a reservation ID on success.
        """)
    public String bookCourt(String facilityId, String dateTimeIso, String playerNames, String recipientId) {
        log.info("bookCourt: facilityId={}, dateTime={}, players={}", facilityId, dateTimeIso, playerNames);
        LocalDateTime dateTime;
        try {
            dateTime = LocalDateTime.parse(dateTimeIso, ISO_FMT);
        } catch (DateTimeParseException e) {
            return "Invalid date/time format. Please use ISO-8601, e.g. 2026-03-20T11:00:00";
        }

        List<String> players = Arrays.stream(playerNames.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();

        if (players.isEmpty()) {
            return "At least one player name is required.";
        }

        String pastValidation = validateNotInPast(facilityId, dateTime);
        if (pastValidation != null) {
            return pastValidation;
        }

        OriginRequestContext origin = directOrigin(recipientId, facilityId);
        BookingIntent intent = new BookingIntent(
            BookingIntent.BookingAction.BOOK,
            dateTime, null, players, List.of(), null, Map.of());

        ReservationHandle handle = bookingService.book(origin, intent);

        log.info("Booking initiated, reservationId={}", handle.reservationId());
        return "Booking request queued (ID: " + handle.reservationId() + "). The system is checking availability asynchronously — the member will receive a separate notification with the outcome. Do NOT tell the member the court is confirmed yet.";
    }

    @FunctionTool(description = """
        Cancel an existing court reservation by its reservation ID.
        The reservation ID was returned when the booking was originally made.
        """)
    public String cancelReservation(String reservationId) {
        log.info("cancelReservation: reservationId={}", reservationId);
        try {
            bookingService.cancel(directOrigin("", ""), new CancelIntent(reservationId));
            return "Cancellation request submitted for reservation " + reservationId + ".";
        } catch (Exception e) {
            log.warn("Cancel failed for reservationId={}: {}", reservationId, e.getMessage());
            return "Could not cancel reservation " + reservationId + ": " + e.getMessage();
        }
    }

    @FunctionTool(description = """
        Look up the details of an existing reservation by its ID.
        Returns the current state (FULFILLED, CANCELLED, etc.), players, date/time, court, and a Google Calendar link.
        Use this when a member asks about a specific reservation ID.
        """)
    public String getReservationDetails(String reservationId) {
        log.info("getReservationDetails: reservationId={}", reservationId);
        try {
            ReservationDetails details = reservationGateway.get(reservationId);
            String courtLabel = details.resourceId();
            String calendarLink = "";
            if (details.resourceId() != null && !details.resourceId().isBlank()) {
                Optional<ResourceV> resource = componentClient.forView()
                    .method(com.rezhub.reservation.resource.ResourceView::getResourceById)
                    .invoke(details.resourceId());
                if (resource.isPresent()) {
                    courtLabel = resource.get().resourceName();
                    String calendarId = resource.get().calendarId();
                    if (calendarId != null && !calendarId.isBlank()) {
                        String eid = Base64.getEncoder().encodeToString(
                            (reservationId + " " + calendarId).getBytes());
                        calendarLink = "\nCalendar: https://calendar.google.com/calendar/event?eid=" + eid;
                    }
                }
            }
            return ("Reservation %s\nState: %s\nCourt: %s\nDate/time: %s%s")
                .formatted(reservationId, details.status(), courtLabel, details.dateTime(), calendarLink);
        } catch (Exception e) {
            log.warn("getReservationDetails failed for {}: {}", reservationId, e.getMessage());
            return "Could not find reservation " + reservationId + ": " + e.getMessage();
        }
    }

    @FunctionTool(description = """
        Resolve a natural-language date/time expression into an ISO-8601 local date-time.
        Use this before checkAvailability or bookCourt whenever the member used phrases like
        today, tomorrow, next Tuesday, oggi, domani, dopodomani, or weekday names.
        timezone must be the facility IANA timezone, for example Europe/Rome.
        Returns either a single ISO-8601 date-time or a short error asking for missing detail.
        """)
    public String resolveDateTime(String text, String timezone) {
        ZoneId zoneId = safeZoneId(timezone);
        Optional<LocalDateTime> resolved = resolveNaturalDateTime(text, zoneId, ZonedDateTime.now(zoneId));
        if (resolved.isEmpty()) {
            return "Could not resolve an exact date and time. Ask the member for a clearer date/time.";
        }
        return resolved.get().format(ISO_FMT);
    }

    // --- helpers ---

    private OriginRequestContext directOrigin(String recipientId, String facilityId) {
        return new OriginRequestContext("direct", "", "", recipientId, facilityId,
            Map.of("facilityId", facilityId));
    }

    private String validateNotInPast(String facilityId, LocalDateTime requestedTime) {
        ZoneId zoneId = facilityZoneId(facilityId);
        LocalDateTime now = LocalDateTime.now(zoneId).minusMinutes(1);
        if (requestedTime.isBefore(now)) {
            return "The requested date/time is in the past for timezone " + zoneId +
                ". Ask the member to confirm a future time.";
        }
        return null;
    }

    private ZoneId facilityZoneId(String facilityId) {
        if (componentClient == null) {
            return ZoneId.of("Europe/Berlin");
        }
        try {
            Facility facility = componentClient
                .forEventSourcedEntity(facilityId)
                .method(FacilityEntity::getFacility)
                .invoke();
            return safeZoneId(facility != null ? facility.timezone() : null);
        } catch (Exception e) {
            log.warn("Could not resolve timezone for facility {}: {}", facilityId, e.getMessage());
            return ZoneId.of("Europe/Berlin");
        }
    }

    static ZoneId safeZoneId(String timezone) {
        try {
            return timezone == null || timezone.isBlank()
                ? ZoneId.of("Europe/Berlin")
                : ZoneId.of(timezone);
        } catch (Exception e) {
            return ZoneId.of("Europe/Berlin");
        }
    }

    static Optional<LocalDateTime> resolveNaturalDateTime(String text, ZoneId zoneId, ZonedDateTime now) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        String normalized = normalize(text);
        LocalDate baseDate = null;

        if (normalized.contains("dopodomani") || normalized.contains("day after tomorrow")) {
            baseDate = now.toLocalDate().plusDays(2);
        } else if (containsWord(normalized, "domani") || normalized.contains("tomorrow")) {
            baseDate = now.toLocalDate().plusDays(1);
        } else if (containsWord(normalized, "oggi") || normalized.contains("today")) {
            baseDate = now.toLocalDate();
        } else {
            for (Map.Entry<String, DayOfWeek> entry : WEEKDAYS.entrySet()) {
                if (containsWord(normalized, entry.getKey())) {
                    baseDate = nextOccurrence(now.toLocalDate(), entry.getValue(), normalized);
                    break;
                }
            }
        }

        if (baseDate == null) {
            return Optional.empty();
        }

        Matcher matcher = TIME_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return Optional.empty();
        }

        int hour = Integer.parseInt(matcher.group(1));
        int minute = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
        String meridiem = matcher.group(3);
        if (meridiem != null) {
            String lower = meridiem.toLowerCase(Locale.ROOT);
            if (lower.equals("pm") && hour < 12) hour += 12;
            if (lower.equals("am") && hour == 12) hour = 0;
        }
        if (hour > 23 || minute > 59) {
            return Optional.empty();
        }

        return Optional.of(LocalDateTime.of(baseDate, java.time.LocalTime.of(hour, minute)));
    }

    private static LocalDate nextOccurrence(LocalDate start, DayOfWeek target, String normalizedText) {
        int current = start.getDayOfWeek().getValue();
        int wanted = target.getValue();
        int delta = (wanted - current + 7) % 7;
        if (delta == 0 || normalizedText.contains("next ") || normalizedText.contains("prossim")) {
            delta = delta == 0 ? 7 : delta;
        }
        return start.plusDays(delta);
    }

    private static boolean containsWord(String text, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
            .replace('à', 'a')
            .replace('è', 'e')
            .replace('é', 'e')
            .replace('ì', 'i')
            .replace('ò', 'o')
            .replace('ù', 'u');
    }
}
