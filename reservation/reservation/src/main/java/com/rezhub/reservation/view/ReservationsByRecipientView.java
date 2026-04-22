package com.rezhub.reservation.view;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.rezhub.reservation.reservation.ReservationEntity;
import com.rezhub.reservation.reservation.ReservationEvent;

import java.util.List;
import java.util.Optional;

@Component(id = "view_reservations_by_recipient")
public class ReservationsByRecipientView extends View {

    public record Entry(
        String reservationId,
        String recipientId,
        String state,
        String dateTime,
        String resourceId,
        String playerNames
    ) {}

    public record Entries(List<Entry> entries) {}

    @Consume.FromEventSourcedEntity(ReservationEntity.class)
    public static class ReservationsByRecipientUpdater extends TableUpdater<Entry> {

        public Effect<Entry> onEvent(ReservationEvent event) {
            String reservationId = updateContext().eventSubject().orElse("");
            return switch (event) {
                case ReservationEvent.Inited e -> effects().updateRow(new Entry(
                    reservationId,
                    e.recipientId(),
                    "COLLECTING",
                    e.reservation().dateTime().toString(),
                    "",
                    String.join(", ", e.reservation().emails())
                ));
                case ReservationEvent.ResourceSelected e ->
                    rowState() == null ? effects().ignore() :
                        effects().updateRow(new Entry(
                            rowState().reservationId(),
                            rowState().recipientId(),
                            "SELECTING",
                            rowState().dateTime(),
                            e.resourceId(),
                            rowState().playerNames()
                        ));
                case ReservationEvent.Rejected ignored ->
                    rowState() == null ? effects().ignore() :
                        effects().updateRow(new Entry(
                            rowState().reservationId(),
                            rowState().recipientId(),
                            "COLLECTING",
                            rowState().dateTime(),
                            "",
                            rowState().playerNames()
                        ));
                case ReservationEvent.Fulfilled e ->
                    rowState() == null ? effects().ignore() :
                        effects().updateRow(new Entry(
                            rowState().reservationId(),
                            rowState().recipientId(),
                            "FULFILLED",
                            rowState().dateTime(),
                            e.resourceId(),
                            rowState().playerNames()
                        ));
                case ReservationEvent.SearchExhausted ignored ->
                    rowState() == null ? effects().ignore() :
                        effects().updateRow(new Entry(
                            rowState().reservationId(),
                            rowState().recipientId(),
                            "UNAVAILABLE",
                            rowState().dateTime(),
                            "",
                            rowState().playerNames()
                        ));
                case ReservationEvent.ReservationCancelled ignored ->
                    rowState() == null ? effects().ignore() :
                        effects().updateRow(new Entry(
                            rowState().reservationId(),
                            rowState().recipientId(),
                            "CANCELLED",
                            rowState().dateTime(),
                            rowState().resourceId(),
                            rowState().playerNames()
                        ));
                case ReservationEvent.CancelRequested ignored -> effects().ignore();
                case ReservationEvent.AvailabilityReplied ignored -> effects().ignore();
            };
        }
    }

    @Query("""
        SELECT * AS entries
        FROM reservations_by_recipient
        WHERE recipientId = :recipient_id
        ORDER BY dateTime DESC
        """)
    public QueryEffect<Entries> getByRecipient(String recipient_id) {
        return queryResult();
    }

    @Query("""
        SELECT *
        FROM reservations_by_recipient
        WHERE recipientId = :recipient_id
        ORDER BY dateTime DESC
        LIMIT 1
        """)
    public QueryEffect<Optional<Entry>> getLatestByRecipient(String recipient_id) {
        return queryResult();
    }
}
