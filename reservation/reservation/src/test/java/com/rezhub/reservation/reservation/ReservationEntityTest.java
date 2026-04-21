package com.rezhub.reservation.reservation;

import akka.javasdk.testkit.EventSourcedTestKit;
import com.rezhub.reservation.dto.EntityType;
import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.dto.SelectionItem;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.rezhub.reservation.reservation.ReservationState.State.*;
import static org.assertj.core.api.Assertions.assertThat;

class ReservationEntityTest {

    private static final String RESERVATION_ID = "rez-001";
    private static final String RESOURCE_ID = "r_court1";
    private static final LocalDateTime SLOT = LocalDateTime.of(2026, 5, 10, 14, 0);

    private EventSourcedTestKit<ReservationState, ReservationEvent, ReservationEntity> freshKit() {
        return EventSourcedTestKit.of(RESERVATION_ID, ReservationEntity::new);
    }

    private EventSourcedTestKit<ReservationState, ReservationEvent, ReservationEntity> inCollecting() {
        var kit = freshKit();
        var reservation = new Reservation(List.of("amy@example.com"), SLOT);
        var selection = Set.of(new SelectionItem(RESOURCE_ID, EntityType.RESOURCE));
        kit.method(ReservationEntity::init).invoke(new ReservationEntity.Init(reservation, selection, "recipient-1"));
        return kit;
    }

    private EventSourcedTestKit<ReservationState, ReservationEvent, ReservationEntity> inSelecting() {
        var kit = inCollecting();
        kit.method(ReservationEntity::replyAvailability)
            .invoke(new ReservationEntity.ReplyAvailability(RESERVATION_ID, RESOURCE_ID, true));
        return kit;
    }

    // --- fulfill() contract (relied upon by compensation logic in ResourceAction) ---

    @Test
    void fulfill_inSelecting_succeedsAndTransitionsToFulfilled() {
        var kit = inSelecting();
        assertThat(kit.getState().state()).isEqualTo(SELECTING);

        var reservation = new Reservation(List.of("amy@example.com"), SLOT);
        var result = kit.method(ReservationEntity::fulfill)
            .invoke(new ReservationEntity.Fulfill(RESOURCE_ID, RESERVATION_ID, reservation));

        assertThat(result.isError()).isFalse();
        assertThat(result.getReply()).startsWith("OK");
        assertThat(result.getNextEventOfType(ReservationEvent.Fulfilled.class)).isNotNull();
        assertThat(kit.getState().state()).isEqualTo(FULFILLED);
    }

    @Test
    void fulfill_inUnavailable_returnsCannotBook() {
        // This is the scenario triggered by the timer/lock race:
        // expire() fires while a resource lock is in flight, leaving the reservation
        // UNAVAILABLE. ResourceAction then calls fulfill() — must return a non-OK
        // reply so the compensation path in ResourceAction knows to release the lock.
        var kit = inSelecting();
        kit.method(ReservationEntity::expire).invoke();
        assertThat(kit.getState().state()).isEqualTo(UNAVAILABLE);

        var reservation = new Reservation(List.of("amy@example.com"), SLOT);
        var result = kit.method(ReservationEntity::fulfill)
            .invoke(new ReservationEntity.Fulfill(RESOURCE_ID, RESERVATION_ID, reservation));

        assertThat(result.isError()).isFalse();
        assertThat(result.getReply()).isEqualTo("Resource cannot be booked");
        assertThat(kit.getState().state()).isEqualTo(UNAVAILABLE);
    }

    // --- expire() behavior ---

    @Test
    void expire_inCollecting_transitionsToUnavailable() {
        var kit = inCollecting();
        var result = kit.method(ReservationEntity::expire).invoke();

        assertThat(result.isError()).isFalse();
        assertThat(result.getNextEventOfType(ReservationEvent.SearchExhausted.class)).isNotNull();
        assertThat(kit.getState().state()).isEqualTo(UNAVAILABLE);
    }

    @Test
    void expire_inSelecting_transitionsToUnavailable() {
        // The entity itself still permits this transition. Protection against
        // premature expiry while a resource lock is in flight is handled in
        // TimerAction (SELECTING guard + re-arm), not in the entity.
        var kit = inSelecting();
        var result = kit.method(ReservationEntity::expire).invoke();

        assertThat(result.isError()).isFalse();
        assertThat(result.getNextEventOfType(ReservationEvent.SearchExhausted.class)).isNotNull();
        assertThat(kit.getState().state()).isEqualTo(UNAVAILABLE);
    }

    @Test
    void expire_inFulfilled_isNoOp() {
        var kit = inSelecting();
        var reservation = new Reservation(List.of("amy@example.com"), SLOT);
        kit.method(ReservationEntity::fulfill)
            .invoke(new ReservationEntity.Fulfill(RESOURCE_ID, RESERVATION_ID, reservation));

        var result = kit.method(ReservationEntity::expire).invoke();
        assertThat(result.isError()).isFalse();
        assertThat(kit.getState().state()).isEqualTo(FULFILLED);
    }
}
