package com.rezhub.reservation.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;
import com.rezhub.reservation.view.ReservationsByRecipientView;

import java.util.List;
import java.util.Optional;

@HttpEndpoint("/reservation-lookup")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class ReservationLookupEndpoint {

    private final ComponentClient componentClient;

    public ReservationLookupEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Get("/recipient/{recipientId}")
    public ReservationsByRecipientView.Entries getByRecipient(String recipientId) {
        return componentClient.forView()
            .method(ReservationsByRecipientView::getByRecipient)
            .invoke(recipientId);
    }

    @Get("/recipient/{recipientId}/latest")
    public ReservationLookupResult getLatestByRecipient(String recipientId) {
        Optional<ReservationsByRecipientView.Entry> result = componentClient.forView()
            .method(ReservationsByRecipientView::getLatestByRecipient)
            .invoke(recipientId);
        return ReservationLookupResult.from(result);
    }

    public record ReservationLookupResult(
        String reservationId,
        String recipientId,
        String state,
        String dateTime,
        String resourceId,
        List<String> players
    ) {
        static ReservationLookupResult from(Optional<ReservationsByRecipientView.Entry> entry) {
            return entry
                .map(e -> new ReservationLookupResult(
                    e.reservationId(),
                    e.recipientId(),
                    e.state(),
                    e.dateTime(),
                    e.resourceId().isBlank() ? null : e.resourceId(),
                    e.playerNames().isBlank() ? List.of() : List.of(e.playerNames().split(", "))
                ))
                .orElse(new ReservationLookupResult(null, null, null, null, null, List.of()));
        }
    }
}
