package com.rezhub.reservation.payment;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

import java.util.Optional;

/**
 * Read-side projection keyed by {@code resourceId + dateTime}, per FR-014. Exists and is populated in
 * this feature but has no consumer yet — Phase 2's rescue-refund lookup ("is there still an open hold
 * on exactly this slot?") is the first real consumer.
 *
 * <p>Sourced from {@code PaymentEntity} alone: {@code resourceId}/{@code dateTime} are denormalized
 * onto {@code PaymentEvent} at first touch (see that class's doc comment), so no cross-entity join
 * with {@code ReservationEntity} is needed. {@code dateTime} is stored as its {@code toString()} — an
 * indexed View column must be a primitive or {@code Instant}, not {@code LocalDateTime}; matches the
 * existing convention already used by {@code ReservationCalendarView}.
 */
@Component(id = "view_slot_payment")
public class SlotPaymentView extends View {

    public record SlotPaymentEntry(String paymentId, String resourceId, String dateTime, String state) {}

    @Consume.FromEventSourcedEntity(PaymentEntity.class)
    public static class SlotPaymentUpdater extends TableUpdater<SlotPaymentEntry> {

        public Effect<SlotPaymentEntry> onEvent(PaymentEvent event) {
            return switch (event) {
                case PaymentEvent.HoldAuthorized e -> effects().updateRow(
                    new SlotPaymentEntry(e.paymentId(), e.resourceId(), e.dateTime().toString(), PaymentState.State.AUTHORIZED.name()));
                case PaymentEvent.HoldCaptured e ->
                    rowState() == null ? effects().ignore() :
                        effects().updateRow(new SlotPaymentEntry(
                            rowState().paymentId(), rowState().resourceId(), rowState().dateTime(),
                            PaymentState.State.CAPTURED.name()));
                case PaymentEvent.HoldCreationFailed e -> effects().updateRow(
                    new SlotPaymentEntry(e.paymentId(), e.resourceId(), e.dateTime().toString(), PaymentState.State.FAILED.name()));
            };
        }
    }

    public record SlotKey(String resourceId, String dateTime) {}

    @Query("SELECT * FROM slot_payment_view WHERE resourceId = :resourceId AND dateTime = :dateTime LIMIT 1")
    public QueryEffect<Optional<SlotPaymentEntry>> getBySlot(SlotKey key) {
        return queryResult();
    }
}
