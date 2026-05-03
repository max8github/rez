package com.rezhub.reservation.reservation;

import akka.javasdk.testkit.EventSourcedTestKit;
import com.rezhub.reservation.dto.Reservation;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.rezhub.reservation.reservation.ReservationState.State.COLLECTING;
import static com.rezhub.reservation.reservation.ReservationState.State.FULFILLED;
import static com.rezhub.reservation.reservation.ReservationState.State.SELECTING;
import static com.rezhub.reservation.reservation.ReservationState.State.UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;

class ReservationEntityTest {

    private static final String RESERVATION_ID = "rez-001";
    private static final String RESOURCE_ID = "r_court1";
    private static final String RESOURCE_ID_2 = "r_court2";
    private static final LocalDateTime SLOT = LocalDateTime.of(2026, 5, 10, 14, 0);

    private EventSourcedTestKit<ReservationState, ReservationEvent, ReservationEntity> freshKit() {
        return EventSourcedTestKit.of(RESERVATION_ID, ReservationEntity::new);
    }

    private EventSourcedTestKit<ReservationState, ReservationEvent, ReservationEntity> inCollecting(Set<String> resourceIds) {
        var kit = freshKit();
        var reservation = new Reservation(List.of("amy@example.com"), SLOT);
        kit.method(ReservationEntity::init).invoke(new ReservationEntity.Init(reservation, resourceIds, "recipient-1", null));
        return kit;
    }

    private EventSourcedTestKit<ReservationState, ReservationEvent, ReservationEntity> inSelectingSingleResource() {
        var kit = inCollecting(Set.of(RESOURCE_ID));
        kit.method(ReservationEntity::replyAvailability)
            .invoke(new ReservationEntity.ReplyAvailability(RESERVATION_ID, RESOURCE_ID, true));
        return kit;
    }

    @Test
    void fulfill_inSelecting_succeedsAndTransitionsToFulfilled() {
        var kit = inSelectingSingleResource();
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
        var kit = inCollecting(Set.of(RESOURCE_ID));
        kit.method(ReservationEntity::replyAvailability)
            .invoke(new ReservationEntity.ReplyAvailability(RESERVATION_ID, RESOURCE_ID, false));
        assertThat(kit.getState().state()).isEqualTo(UNAVAILABLE);

        var reservation = new Reservation(List.of("amy@example.com"), SLOT);
        var result = kit.method(ReservationEntity::fulfill)
            .invoke(new ReservationEntity.Fulfill(RESOURCE_ID, RESERVATION_ID, reservation));

        assertThat(result.isError()).isFalse();
        assertThat(result.getReply()).isEqualTo("Resource cannot be booked");
        assertThat(kit.getState().state()).isEqualTo(UNAVAILABLE);
    }

    @Test
    void replyAvailability_lastNegative_transitionsToUnavailable() {
        var kit = inCollecting(Set.of(RESOURCE_ID));

        var result = kit.method(ReservationEntity::replyAvailability)
            .invoke(new ReservationEntity.ReplyAvailability(RESERVATION_ID, RESOURCE_ID, false));

        assertThat(result.isError()).isFalse();
        assertThat(result.getNextEventOfType(ReservationEvent.SearchExhausted.class)).isNotNull();
        assertThat(kit.getState().state()).isEqualTo(UNAVAILABLE);
    }

    @Test
    void reject_withoutAlternativeButWithPendingReplies_returnsToCollecting() {
        var kit = inCollecting(Set.of(RESOURCE_ID, RESOURCE_ID_2));
        kit.method(ReservationEntity::replyAvailability)
            .invoke(new ReservationEntity.ReplyAvailability(RESERVATION_ID, RESOURCE_ID, true));

        var result = kit.method(ReservationEntity::reject)
            .invoke(new ReservationEntity.Reject(RESOURCE_ID));

        assertThat(result.isError()).isFalse();
        assertThat(result.getNextEventOfType(ReservationEvent.Rejected.class)).isNotNull();
        assertThat(kit.getState().state()).isEqualTo(COLLECTING);
        assertThat(kit.getState().pendingResourceIds()).contains(RESOURCE_ID_2);
    }

    @Test
    void reject_withAlternativeAvailable_selectsNextResourceImmediately() {
        var kit = inCollecting(Set.of(RESOURCE_ID, RESOURCE_ID_2));
        kit.method(ReservationEntity::replyAvailability)
            .invoke(new ReservationEntity.ReplyAvailability(RESERVATION_ID, RESOURCE_ID, true));
        kit.method(ReservationEntity::replyAvailability)
            .invoke(new ReservationEntity.ReplyAvailability(RESERVATION_ID, RESOURCE_ID_2, true));

        var result = kit.method(ReservationEntity::reject)
            .invoke(new ReservationEntity.Reject(RESOURCE_ID));

        var selected = result.getNextEventOfType(ReservationEvent.ResourceSelected.class);
        assertThat(result.isError()).isFalse();
        assertThat(selected).isNotNull();
        assertThat(selected.resourceId()).isEqualTo(RESOURCE_ID_2);
        assertThat(kit.getState().state()).isEqualTo(SELECTING);
        assertThat(kit.getState().resourceId()).isEqualTo(RESOURCE_ID_2);
    }

    @Test
    void reject_withoutAlternativeAndWithoutPendingReplies_exhaustsSearch() {
        var kit = inSelectingSingleResource();

        var result = kit.method(ReservationEntity::reject)
            .invoke(new ReservationEntity.Reject(RESOURCE_ID));

        assertThat(result.isError()).isFalse();
        assertThat(result.getNextEventOfType(ReservationEvent.SearchExhausted.class)).isNotNull();
        assertThat(kit.getState().state()).isEqualTo(UNAVAILABLE);
    }
}
