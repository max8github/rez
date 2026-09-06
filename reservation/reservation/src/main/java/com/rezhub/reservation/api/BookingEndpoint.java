package com.rezhub.reservation.api;

import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.payment.PaymentGate;
import com.rezhub.reservation.reservation.ReservationEntity;
import com.rezhub.reservation.reservation.ReservationState;
import com.rezhub.reservation.resource.ResourceEntity;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * External booking API for non-AI callers that already know which resources to book.
 * Accepts a flat set of resourceIds. BookingService resolves facility → resourceIds externally.
 */
@HttpEndpoint("/bookings")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class BookingEndpoint {

    private static final Logger log = LoggerFactory.getLogger(BookingEndpoint.class);

    private final ComponentClient componentClient;
    private final PaymentGate paymentGate;

    public BookingEndpoint(ComponentClient componentClient, PaymentGate paymentGate) {
        this.componentClient = componentClient;
        this.paymentGate = paymentGate;
    }

    /**
     * Create a reservation from a flat set of resource IDs.
     * The caller is responsible for providing a unique reservationId (e.g. its own session/request ID).
     *
     * <p>FR-012 only: this path carries no player identity at all (the {@code Init} below already
     * hardcodes it to {@code Optional.empty()}, predating payments), so only the facility-side
     * payability gate applies here — see research.md #10 and spec.md's Out of Scope. Returns
     * {@code 400} rather than submitting a reservation that would later fail unrecoverably at its
     * commitment cutoff with no one to notify.
     */
    @Post("")
    public HttpResponse book(BookingRequest request) {
        log.info("BookingEndpoint: creating reservation {} for resources {}", request.reservationId(), request.resourceIds());

        Optional<String> facilityId = request.resourceIds().stream().findFirst()
            .map(resourceId -> componentClient.forEventSourcedEntity(resourceId)
                .method(ResourceEntity::getResource)
                .invoke()
                .externalGroupRef());
        if (facilityId.isPresent() && !facilityId.get().isBlank() && !paymentGate.isFacilityPayable(facilityId.get())) {
            log.info("BookingEndpoint: rejecting reservation {} — facility {} has a PricingPolicy but incomplete Stripe onboarding",
                request.reservationId(), facilityId.get());
            return HttpResponses.badRequest("Facility is not currently able to accept payments");
        }

        var reservation = request.durationMinutes() != null
            ? new Reservation(request.emails(), request.dateTime(), request.durationMinutes())
            : new Reservation(request.emails(), request.dateTime());
        var init = new ReservationEntity.Init(reservation, request.resourceIds(), request.recipientId(), request.originSystem(),
            Optional.empty(), Optional.empty());
        componentClient
            .forEventSourcedEntity(request.reservationId())
            .method(ReservationEntity::init)
            .invoke(init);

        return HttpResponses.ok(new BookingResult(request.reservationId(), ReservationState.State.COLLECTING.name(), null, request.dateTime()));
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
        Integer durationMinutes,
        List<String> emails,
        Set<String> resourceIds,
        String recipientId,
        String originSystem
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
