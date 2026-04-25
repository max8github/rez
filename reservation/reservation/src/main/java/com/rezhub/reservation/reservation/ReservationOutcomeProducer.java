package com.rezhub.reservation.reservation;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Produce;
import akka.javasdk.consumer.Consumer;

/**
 * Publishes reservation outcomes to the "reservation-outcomes" service stream
 * so that other Akka services in the same project (e.g. hit-backend) can subscribe
 * without polling.
 *
 * Only terminal events (Fulfilled and SearchExhausted/Rejected) are forwarded.
 */
@Component(id = "reservation-outcomes-producer")
@Consume.FromEventSourcedEntity(ReservationEntity.class)
@Produce.ServiceStream(id = "reservation-outcomes")
@Acl(allow = @Acl.Matcher(service = "*"))
public class ReservationOutcomeProducer extends Consumer {

    public Effect onEvent(ReservationEvent event) {
        return switch (event) {
            case ReservationEvent.Fulfilled f ->
                effects().produce(new ReservationOutcomeEvent.Fulfilled(f.reservationId(), f.resourceId()));
            case ReservationEvent.SearchExhausted e ->
                effects().produce(new ReservationOutcomeEvent.Rejected(e.reservationId()));
            case ReservationEvent.Rejected r ->
                effects().produce(new ReservationOutcomeEvent.Rejected(r.reservationId()));
            default -> effects().ignore();
        };
    }
}
