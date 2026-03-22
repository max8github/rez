package com.rezhub.reservation.agent;

import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timer.TimerScheduler;
import com.rezhub.reservation.actions.RezAction;
import com.rezhub.reservation.actions.TimerAction;
import com.rezhub.reservation.dto.EntityType;
import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.dto.SelectionItem;
import com.rezhub.reservation.reservation.ReservationEntity;
import com.rezhub.reservation.resource.ResourceV;
import com.rezhub.reservation.resource.ResourceView;
import com.rezhub.reservation.resource.dto.Resource;
import com.rezhub.reservation.spi.CalendarSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Provides booking-related function tools for BookingAgent.
 * Each @FunctionTool method is callable by the LLM during a conversation.
 */
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ComponentClient componentClient;
    private final TimerScheduler timerScheduler;

    public BookingService(ComponentClient componentClient, TimerScheduler timerScheduler) {
        this.componentClient = componentClient;
        this.timerScheduler = timerScheduler;
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

        String reservationId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Reservation reservation = new Reservation(players, dateTime);
        ReservationEntity.Init command = new ReservationEntity.Init(reservation,
            Set.of(new SelectionItem(internalFacilityId, EntityType.FACILITY)), recipientId);

        timerScheduler.createSingleTimer(
            RezAction.timerName(reservationId),
            Duration.ofSeconds(RezAction.TIMEOUT),
            componentClient.forTimedAction().method(TimerAction::expire).deferred(reservationId)
        );

        ReservationEntity.ReservationId result = componentClient
            .forEventSourcedEntity(reservationId)
            .method(ReservationEntity::init)
            .invoke(command);

        log.info("Booking initiated, reservationId={}", result.reservationId());
        return "Booking request submitted. Reservation ID: " + result.reservationId() + ". The system is processing it.";
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

    // --- helpers ---

    private String toInternalFacilityId(String facilityId) {
        return facilityId;
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
                return "The next available slot is around " + candidate.format(DateTimeFormatter.ofPattern("HH:mm")) + ".";
            }
        }
        return "No alternative slots found in the next 4 hours.";
    }
}
