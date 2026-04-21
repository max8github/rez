// TODO: this view projects raw booking facts only. Enrichment with facility name, timezone,
//       and i18n belongs in a dedicated Calendar service that consumes Rez events externally.
package com.rezhub.reservation.calendar;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.rezhub.reservation.reservation.ReservationEntity;
import com.rezhub.reservation.reservation.ReservationEvent;

import java.util.List;

@Component(id = "view_reservation_calendar")
public class ReservationCalendarView extends View {

    public record ReservationEntry(
        String reservationId,
        String resourceId,
        String startTime,
        String endTime,
        String playerNames,
        String status
    ) {}

    public record ReservationEntries(List<ReservationEntry> reservations) {}
    public record ReservationRange(String startInclusive, String endExclusive) {}

    @Consume.FromEventSourcedEntity(ReservationEntity.class)
    public static class ReservationCalendarUpdater extends TableUpdater<ReservationEntry> {

        public Effect<ReservationEntry> onEvent(ReservationEvent event) {
            String reservationId = updateContext().eventSubject().orElse("");
            return switch (event) {
                case ReservationEvent.Fulfilled e -> {
                    var startTime = e.reservation().dateTime();
                    yield effects().updateRow(new ReservationEntry(
                        reservationId,
                        e.resourceId(),
                        startTime.toString(),
                        // Reservations are modeled as one rounded booking slot in current Rez state.
                        startTime.plusHours(1).toString(),
                        String.join(", ", e.reservation().emails()),
                        "FULFILLED"
                    ));
                }
                case ReservationEvent.ReservationCancelled ignored ->
                    rowState() == null ? effects().ignore() :
                        effects().updateRow(new ReservationEntry(
                            rowState().reservationId(),
                            rowState().resourceId(),
                            rowState().startTime(),
                            rowState().endTime(),
                            rowState().playerNames(),
                            "CANCELLED"
                        ));
                case ReservationEvent.Inited ignored -> effects().ignore();
                case ReservationEvent.CancelRequested ignored -> effects().ignore();
                case ReservationEvent.SearchExhausted ignored -> effects().ignore();
                case ReservationEvent.RejectedWithNext ignored -> effects().ignore();
                case ReservationEvent.Rejected ignored -> effects().ignore();
                case ReservationEvent.AvailabilityReplied ignored -> effects().ignore();
                case ReservationEvent.ResourceSelected ignored -> effects().ignore();
            };
        }
    }

    @Query("""
        SELECT * AS reservations
        FROM reservation_calendar
        WHERE status = 'FULFILLED'
          AND startTime < :endExclusive
          AND endTime > :startInclusive
        """)
    public QueryEffect<ReservationEntries> getEvents(ReservationRange range) {
        return queryResult();
    }
}
