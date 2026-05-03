package com.rezhub.reservation.orchestration;

import akka.javasdk.client.ComponentClient;
import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.reservation.ReservationEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Akka-backed implementation of ReservationGateway.
 *
 * submit() is fire-and-forward: it initialises the ReservationEntity and returns a handle
 * immediately. The booking outcome (accepted/rejected) arrives asynchronously via the
 * notification path (DelegatingServiceAction / NotificationSender).
 */
public class ReservationGatewayAkka implements ReservationGateway {
    private static final Logger log = LoggerFactory.getLogger(ReservationGatewayAkka.class);

    private final ComponentClient componentClient;

    public ReservationGatewayAkka(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Override
    public ReservationHandle submit(ReservationSubmission submission) {
        log.info("Submitting reservation {} for {} resources at {}",
            submission.reservationId(), submission.resourceIds().size(), submission.dateTime());
        Reservation reservation = new Reservation(submission.participants(), submission.dateTime());
        ReservationEntity.Init command = new ReservationEntity.Init(
            reservation, submission.resourceIds(), submission.recipientId(), submission.originSystem());

        componentClient
            .forEventSourcedEntity(submission.reservationId())
            .method(ReservationEntity::init)
            .invoke(command);

        return new ReservationHandle(submission.reservationId());
    }

    @Override
    public void cancel(String reservationId) {
        log.info("Cancelling reservation {}", reservationId);
        componentClient
            .forEventSourcedEntity(reservationId)
            .method(ReservationEntity::cancelRequest)
            .invoke();
    }

    @Override
    public ReservationDetails get(String reservationId) {
        var state = componentClient
            .forEventSourcedEntity(reservationId)
            .method(ReservationEntity::getReservation)
            .invoke();
        return new ReservationDetails(
            state.reservationId(),
            state.state().name(),
            state.resourceId(),
            state.dateTime());
    }
}
