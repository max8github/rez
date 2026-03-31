package com.rezhub.reservation.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;
import com.rezhub.reservation.reservation.ReservationEntity;
import com.rezhub.reservation.reservation.ReservationState;

import java.time.LocalDateTime;
import java.util.List;

@HttpEndpoint("/reservation")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class ReservationEndpoint {

    private final ComponentClient componentClient;

    public ReservationEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Get("/{reservationId}")
    public ReservationDetails getReservation(String reservationId) {
        ReservationState state = componentClient
            .forEventSourcedEntity(reservationId)
            .method(ReservationEntity::getReservation)
            .invoke();
        return ReservationDetails.from(state);
    }

    public record ReservationDetails(
        String reservationId,
        String state,
        List<String> players,
        LocalDateTime dateTime,
        String resourceId
    ) {
        static ReservationDetails from(ReservationState s) {
            return new ReservationDetails(
                s.reservationId(),
                s.state().name(),
                s.emails(),
                s.dateTime(),
                s.resourceId()
            );
        }
    }
}
