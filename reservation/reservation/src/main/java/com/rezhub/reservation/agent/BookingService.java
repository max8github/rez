package com.rezhub.reservation.agent;

import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;
import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.view.ResourcesByFacilityView;
import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.reservation.ReservationEntity;
import com.rezhub.reservation.resource.ResourceV;
import com.rezhub.reservation.resource.ResourceView;
import com.rezhub.reservation.resource.dto.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides booking-related function tools for BookingAgent.
 * Each @FunctionTool method is callable by the LLM during a conversation.
 */
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter HOUR_ONLY_FMT = DateTimeFormatter.ofPattern("HH:mm");
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

    private final ComponentClient componentClient;

    public BookingService(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    /**
     * Check which courts are free at a facility for a given date/time.
     *
     * @param facilityId  the facility entity ID
     * @param dateTimeIso the requested date/time in ISO-8601 format, e.g. "2026-03-20T11:00:00"
     * @return human-readable description of available courts, or "no courts available"
     */
    @FunctionTool(description = """
        Check court availability at a facility for a specific date and time.
        Returns a list of available courts or states that none are free.
        Call this before booking to know what slots exist.
        """)
    public String checkAvailability(String facilityId, String dateTimeIso) {
        String internalFacilityId = toInternalFacilityId(facilityId);
        log.info("checkAvailability: facilityId={}, dateTime={}", internalFacilityId, dateTimeIso);
        LocalDateTime requestedTime;
        try {
            requestedTime = LocalDateTime.parse(dateTimeIso, ISO_FMT);
        } catch (DateTimeParseException e) {
            return "Invalid date/time format. Please use ISO-8601, e.g. 2026-03-20T11:00:00";
        }

        String pastValidation = validateNotInPast(internalFacilityId, requestedTime);
        if (pastValidation != null) {
            return pastValidation;
        }

        ResourceView.Resources resources = componentClient
            .forView()
            .method(ResourceView::getResource)
            .invoke(internalFacilityId);

        if (resources.resources().isEmpty()) {
            return "No courts registered for this facility.";
        }

        List<String> available = resources.resources().stream()
            .filter(r -> isAvailableAt(r, requestedTime))
            .map(r -> r.resourceName() + " (id: " + r.resourceId() + ")")
            .toList();

        if (available.isEmpty()) {
            // Also surface nearby available slots to help the LLM suggest alternatives
            String alternatives = findNearbySlots(resources.resources(), requestedTime);
            return "No courts available at " + dateTimeIso + ". " + alternatives;
        }

        return "Available courts at " + dateTimeIso + ": " + String.join(", ", available);
    }

    /**
     * Book a court at a facility.
     *
     * @param facilityId   the facility entity ID
     * @param dateTimeIso  the booking date/time in ISO-8601, e.g. "2026-03-20T11:00:00"
     * @param playerNames  comma-separated display names of the players (e.g. "Max,John")
     * @return confirmation message with reservation ID, or error description
     */
    @FunctionTool(description = """
        Book a court at a facility for specific players at a specific date and time.
        Always call checkAvailability first to confirm a slot is free.
        playerNames must be a comma-separated list of the players' display names (e.g. "Max,John").
        Use the sender's name for the person making the request.
        recipientId is the notification recipient identifier found in the [recipient:X] message prefix — always pass it unchanged.
        Returns a reservation ID on success.
        """)
    public String bookCourt(String facilityId, String dateTimeIso, String playerNames, String recipientId) {
        String internalFacilityId = toInternalFacilityId(facilityId);
        log.info("bookCourt: facilityId={}, dateTime={}, players={}", internalFacilityId, dateTimeIso, playerNames);
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

        String pastValidation = validateNotInPast(internalFacilityId, dateTime);
        if (pastValidation != null) {
            return pastValidation;
        }

        String reservationId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Reservation reservation = new Reservation(players, dateTime);
        Set<String> resourceIds = componentClient
            .forView()
            .method(ResourcesByFacilityView::getByFacilityId)
            .invoke(internalFacilityId)
            .rows().stream()
            .map(ResourcesByFacilityView.Row::resourceId)
            .collect(java.util.stream.Collectors.toSet());
        ReservationEntity.Init command = new ReservationEntity.Init(reservation, resourceIds, recipientId);

        ReservationEntity.ReservationId result = componentClient
            .forEventSourcedEntity(reservationId)
            .method(ReservationEntity::init)
            .invoke(command);

        log.info("Booking initiated, reservationId={}", result.reservationId());
        return "Booking request queued (ID: " + result.reservationId() + "). The system is checking availability asynchronously — the member will receive a separate notification with the outcome. Do NOT tell the member the court is confirmed yet.";
    }

    /**
     * Cancel an existing reservation.
     *
     * @param reservationId the reservation ID returned when the court was booked
     * @return confirmation or error message
     */
    @FunctionTool(description = """
        Cancel an existing court reservation by its reservation ID.
        The reservation ID was returned when the booking was originally made.
        """)
    public String cancelReservation(String reservationId) {
        log.info("cancelReservation: reservationId={}", reservationId);
        try {
            componentClient
                .forEventSourcedEntity(reservationId)
                .method(ReservationEntity::cancelRequest)
                .invoke();
            return "Cancellation request submitted for reservation " + reservationId + ".";
        } catch (Exception e) {
            log.warn("Cancel failed for reservationId={}: {}", reservationId, e.getMessage());
            return "Could not cancel reservation " + reservationId + ": " + e.getMessage();
        }
    }

    /**
     * Look up the details of an existing reservation by its ID.
     *
     * @param reservationId the reservation ID
     * @return reservation details including state, players, date/time, court, and a calendar link
     */
    @FunctionTool(description = """
        Look up the details of an existing reservation by its ID.
        Returns the current state (FULFILLED, CANCELLED, etc.), players, date/time, court, and a Google Calendar link.
        Use this when a member asks about a specific reservation ID.
        """)
    public String getReservationDetails(String reservationId) {
        log.info("getReservationDetails: reservationId={}", reservationId);
        try {
            var state = componentClient
                .forEventSourcedEntity(reservationId)
                .method(ReservationEntity::getReservation)
                .invoke();

            String courtLabel = state.resourceId();
            String calendarLink = "";
            if (!state.resourceId().isBlank()) {
                Optional<ResourceV> resource = componentClient.forView()
                    .method(ResourceView::getResourceById)
                    .invoke(state.resourceId());
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

            return ("Reservation %s\nState: %s\nCourt: %s\nDate/time: %s\nPlayers: %s%s")
                .formatted(reservationId, state.state(), courtLabel, state.dateTime(),
                    String.join(", ", state.emails()), calendarLink);
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

    private String toInternalFacilityId(String facilityId) {
        return facilityId;
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

    private boolean isAvailableAt(ResourceV resource, LocalDateTime requestedTime) {
        String isoKey = requestedTime.format(ISO_FMT);
        return resource.timeWindow().stream()
            .noneMatch(entry -> entry.dateTime().equals(isoKey));
    }

    private String findNearbySlots(List<ResourceV> resources, LocalDateTime around) {
        // Collect all booked times across all courts, find first gap
        List<LocalDateTime> bookedTimes = resources.stream()
            .flatMap(r -> r.timeWindow().stream())
            .map(Resource.Entry::dateTime)
            .map(s -> { try { return LocalDateTime.parse(s, ISO_FMT); } catch (Exception e) { return null; } })
            .filter(t -> t != null && !t.isBefore(around.minusHours(2)) && !t.isAfter(around.plusHours(4)))
            .distinct()
            .sorted()
            .toList();

        if (bookedTimes.isEmpty()) {
            return "The facility appears to have open slots — try a slightly different time.";
        }

        // Suggest the next whole hour after the requested time that isn't fully booked
        for (int offset = 1; offset <= 4; offset++) {
            LocalDateTime candidate = around.plusHours(offset).withMinute(0).withSecond(0).withNano(0);
            String key = candidate.format(ISO_FMT);
            long busyCourts = resources.stream()
                .filter(r -> r.timeWindow().stream().anyMatch(e -> e.dateTime().equals(key)))
                .count();
            if (busyCourts < resources.size()) {
                return "The next available slot is around " + candidate.format(HOUR_ONLY_FMT) + ".";
            }
        }
        return "No alternative slots found in the next 4 hours.";
    }
}
