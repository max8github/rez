package com.rezhub.reservation.reservation;

import akka.javasdk.testkit.EventSourcedTestKit;
import com.rezhub.reservation.dto.Reservation;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
        kit.method(ReservationEntity::init).invoke(new ReservationEntity.Init(reservation, resourceIds, "recipient-1", null, Optional.empty(), Optional.empty()));
        return kit;
    }

    private EventSourcedTestKit<ReservationState, ReservationEvent, ReservationEntity> inSelectingSingleResource() {
        var kit = inCollecting(Set.of(RESOURCE_ID));
        kit.method(ReservationEntity::replyAvailability)
            .invoke(new ReservationEntity.ReplyAvailability(RESERVATION_ID, RESOURCE_ID, true));
        return kit;
    }

    @Test
    void init_replayWithIdenticalDetails_inCollecting_succeedsWithoutReinitializing() {
        var kit = inCollecting(Set.of(RESOURCE_ID));
        var reservation = new Reservation(List.of("amy@example.com"), SLOT);

        var result = kit.method(ReservationEntity::init)
            .invoke(new ReservationEntity.Init(reservation, Set.of(RESOURCE_ID), "recipient-1", null, Optional.empty(), Optional.empty()));

        assertThat(result.isError()).isFalse();
        assertThat(result.getReply().reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(kit.getState().state()).isEqualTo(COLLECTING);
    }

    @Test
    void init_replayWithIdenticalDetails_inFulfilled_succeedsWithoutReinitializing() {
        var kit = inSelectingSingleResource();
        var reservation = new Reservation(List.of("amy@example.com"), SLOT);
        kit.method(ReservationEntity::fulfill)
            .invoke(new ReservationEntity.Fulfill(RESOURCE_ID, RESERVATION_ID, reservation));

        var result = kit.method(ReservationEntity::init)
            .invoke(new ReservationEntity.Init(reservation, Set.of(RESOURCE_ID), "recipient-1", null, Optional.empty(), Optional.empty()));

        assertThat(result.isError()).isFalse();
        assertThat(result.getReply().reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(kit.getState().state()).isEqualTo(FULFILLED);
    }

    @Test
    void init_duplicateWithDifferentDetails_inCollecting_stillErrors() {
        var kit = inCollecting(Set.of(RESOURCE_ID));
        var differentReservation = new Reservation(List.of("someone-else@example.com"), SLOT.plusHours(1));

        var result = kit.method(ReservationEntity::init)
            .invoke(new ReservationEntity.Init(differentReservation, Set.of(RESOURCE_ID_2), "recipient-2", null, Optional.empty(), Optional.empty()));

        assertThat(result.isError()).isTrue();
    }

    @Test
    void init_duplicateWithDifferentDetails_inFulfilled_stillErrors() {
        var kit = inSelectingSingleResource();
        var reservation = new Reservation(List.of("amy@example.com"), SLOT);
        kit.method(ReservationEntity::fulfill)
            .invoke(new ReservationEntity.Fulfill(RESOURCE_ID, RESERVATION_ID, reservation));

        var differentReservation = new Reservation(List.of("amy@example.com"), SLOT.plusHours(1));
        var result = kit.method(ReservationEntity::init)
            .invoke(new ReservationEntity.Init(differentReservation, Set.of(RESOURCE_ID), "recipient-1", null, Optional.empty(), Optional.empty()));

        assertThat(result.isError()).isTrue();
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

    @Test
    void init_withResolvedIdentity_persistsIdentityUserIdAndSenderExternalId() {
        var kit = freshKit();
        var reservation = new Reservation(List.of("amy@example.com"), SLOT);

        kit.method(ReservationEntity::init).invoke(new ReservationEntity.Init(
            reservation, Set.of(RESOURCE_ID), "recipient-1", "telegram",
            Optional.of("user-abc-123"), Optional.of("tg-98765")));

        assertThat(kit.getState().identityUserId()).contains("user-abc-123");
        assertThat(kit.getState().senderExternalId()).contains("tg-98765");
    }

    @Test
    void init_withoutResolvedIdentity_persistsEmptyOptionals() {
        var kit = inCollecting(Set.of(RESOURCE_ID));

        assertThat(kit.getState().identityUserId()).isEmpty();
        assertThat(kit.getState().senderExternalId()).isEmpty();
    }

    @Test
    void init_replayWithDifferentResolvedIdentity_stillTreatedAsSafeReplay() {
        var kit = freshKit();
        var reservation = new Reservation(List.of("amy@example.com"), SLOT);

        kit.method(ReservationEntity::init).invoke(new ReservationEntity.Init(
            reservation, Set.of(RESOURCE_ID), "recipient-1", "telegram",
            Optional.empty(), Optional.of("tg-98765")));

        // Simulates identity recovering mid-retry: same booking details, but this time resolution succeeded.
        var result = kit.method(ReservationEntity::init).invoke(new ReservationEntity.Init(
            reservation, Set.of(RESOURCE_ID), "recipient-1", "telegram",
            Optional.of("user-abc-123"), Optional.of("tg-98765")));

        assertThat(result.isError()).isFalse();
        assertThat(result.getReply().reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(kit.getState().state()).isEqualTo(COLLECTING);
    }

    @Test
    void getReservation_separateLaterQuery_stillReturnsPersistedIdentity() {
        var kit = freshKit();
        var reservation = new Reservation(List.of("amy@example.com"), SLOT);

        kit.method(ReservationEntity::init).invoke(new ReservationEntity.Init(
            reservation, Set.of(RESOURCE_ID), "recipient-1", "telegram",
            Optional.of("user-abc-123"), Optional.of("tg-98765")));

        // A separate, later command — not the original init() reply — proving the identity is durable,
        // not just visible in the request that resolved it (spec.md User Story 3).
        var state = kit.method(ReservationEntity::getReservation).invoke().getReply();

        assertThat(state.identityUserId()).contains("user-abc-123");
        assertThat(state.senderExternalId()).contains("tg-98765");
    }

    @Test
    void getReservation_whenResolutionFailed_hasNoIdentityUserIdButStillHasSenderExternalId() {
        var kit = freshKit();
        var reservation = new Reservation(List.of("amy@example.com"), SLOT);

        // Resolution failed (e.g. identity unreachable), but the raw sender id was still captured — FR-008.
        kit.method(ReservationEntity::init).invoke(new ReservationEntity.Init(
            reservation, Set.of(RESOURCE_ID), "recipient-1", "telegram",
            Optional.empty(), Optional.of("tg-98765")));

        var state = kit.method(ReservationEntity::getReservation).invoke().getReply();

        assertThat(state.identityUserId()).isEmpty();
        assertThat(state.senderExternalId()).contains("tg-98765");
    }
}
