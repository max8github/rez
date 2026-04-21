package com.rezhub.reservation.api;

import com.rezhub.reservation.actions.TimerAction;
import com.rezhub.reservation.dto.EntityType;
import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.dto.SelectionItem;
import com.rezhub.reservation.reservation.ReservationEntity;
import com.rezhub.reservation.reservation.ReservationState;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timer.TimerScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * External booking API for non-AI callers that already know which resources to book.
 * Accepts a flat set of resourceIds — no Facility or SelectionItem knowledge required.
 * The existing /selection endpoint (RezAction) is unchanged for BookingService/Telegram.
 */
@HttpEndpoint("/bookings")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class BookingEndpoint {

    private static final Logger log = LoggerFactory.getLogger(BookingEndpoint.class);

    private final ComponentClient componentClient;
    private final TimerScheduler timerScheduler;

    public BookingEndpoint(ComponentClient componentClient, TimerScheduler timerScheduler) {
        this.componentClient = componentClient;
        this.timerScheduler = timerScheduler;
    }

    /**
     * Create a reservation from a flat set of resource IDs.
     * The caller is responsible for providing a unique reservationId (e.g. its own session/request ID).
     */
    @SuppressWarnings("deprecation")
    @Post("")
    public BookingResult book(BookingRequest request) {
        log.info("BookingEndpoint: creating reservation {} for resources {}", request.reservationId(), request.resourceIds());

        // SelectionItem is the internal wire type for ReservationEntity.Init.
        // BookingEndpoint translates the public flat-resourceId API to it here so
        // callers never need to know about SelectionItem.
        Set<SelectionItem> selection = request.resourceIds().stream()
            .map(id -> new SelectionItem(id, EntityType.RESOURCE))
            .collect(Collectors.toUnmodifiableSet());

        timerScheduler.createSingleTimer(
            TimerAction.timerName(request.reservationId()),
            Duration.ofSeconds(TimerAction.TIMEOUT_SECONDS),
            componentClient.forTimedAction().method(TimerAction::expire).deferred(request.reservationId())
        );

        var reservation = new Reservation(request.emails(), request.dateTime());
        var init = new ReservationEntity.Init(reservation, selection, request.recipientId());
        componentClient
            .forEventSourcedEntity(request.reservationId())
            .method(ReservationEntity::init)
            .invoke(init);

        return new BookingResult(request.reservationId(), ReservationState.State.COLLECTING.name(), null, request.dateTime());
    }

    /**
     * Get the current booking status and result.
     */
    @Get("/{reservationId}")
    public BookingResult getBooking(String reservationId) {
        ReservationState state = componentClient
            .forEventSourcedEntity(reservationId)
            .method(ReservationEntity::getReservation)
            .invoke();
        return BookingResult.from(state);
    }

    /**
     * Cancel an active booking. Only valid when FULFILLED or COLLECTING.
     */
    @Delete("/{reservationId}")
    public BookingResult cancelBooking(String reservationId) {
        componentClient
            .forEventSourcedEntity(reservationId)
            .method(ReservationEntity::cancelRequest)
            .invoke();
        ReservationState state = componentClient
            .forEventSourcedEntity(reservationId)
            .method(ReservationEntity::getReservation)
            .invoke();
        return BookingResult.from(state);
    }

    public record BookingRequest(
        String reservationId,
        LocalDateTime dateTime,
        List<String> emails,
        Set<String> resourceIds,
        String recipientId
    ) {}

    public record BookingResult(
        String reservationId,
        String status,
        String resourceId,
        LocalDateTime dateTime
    ) {
        static BookingResult from(ReservationState s) {
            return new BookingResult(
                s.reservationId(),
                s.state().name(),
                s.resourceId().isEmpty() ? null : s.resourceId(),
                s.dateTime()
            );
        }
    }
}
