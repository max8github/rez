package com.rezhub.reservation.payment;

import akka.javasdk.testkit.TestKitSupport;
import com.rezhub.reservation.customer.dto.Address;
import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.reservation.ReservationEntity;
import com.rezhub.reservation.resource.ResourceEntity;
import com.rezhub.reservation.resource.dto.Resource;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class PaymentSchedulingActionIntegrationTest extends TestKitSupport {

    @Test
    public void fulfilledReservation_withPricingPolicy_recordsPaymentId() throws Exception {
        String resourceId = "court-" + shortId();
        String reservationId = "reservation-" + shortId();
        // slotStart far enough in the past relative to commitmentWindow that the cutoff clamps to "now" —
        // makes the commitment-cutoff Timer fire immediately, keeping the test fast.
        LocalDateTime slotStart = LocalDateTime.now().plusHours(1);
        Reservation reservation = new Reservation(java.util.List.of("Alice"), slotStart);

        componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::create)
            .invoke(new Resource(resourceId, "Court 1", null));
        componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::setPricingPolicyOverride)
            .invoke(new PricingPolicy(5000, "eur", 0.10, Duration.ofDays(1)));

        componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::init)
            .invoke(new ReservationEntity.Init(reservation, Set.of(resourceId), "telegram-user", "telegram",
                Optional.empty(), Optional.empty()));
        componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::replyAvailability)
            .invoke(new ReservationEntity.ReplyAvailability(reservationId, resourceId, true));
        componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::fulfill)
            .invoke(new ReservationEntity.Fulfill(resourceId, reservationId, reservation));

        var state = eventually(() ->
                componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::getReservation).invoke(),
            s -> s.paymentId().isPresent());

        assertThat(state.paymentId()).contains(reservationId);
    }

    @Test
    public void fulfilledReservation_withNoPricingPolicy_neverCreatesPayment() throws Exception {
        String facilityId = "f_" + shortId();
        String resourceId = "court-" + shortId();
        String reservationId = "reservation-" + shortId();
        LocalDateTime slotStart = LocalDateTime.now().plusHours(2);
        Reservation reservation = new Reservation(java.util.List.of("Bob"), slotStart);

        componentClient.forEventSourcedEntity(facilityId).method(FacilityEntity::create)
            .invoke(new Facility("Free Club", new Address("St", "City"), "Europe/Rome", null, null));
        componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::create)
            .invoke(new Resource(resourceId, "Court 2", null));
        componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::setExternalRef)
            .invoke(new ResourceEntity.SetExternalRef(resourceId, facilityId));

        componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::init)
            .invoke(new ReservationEntity.Init(reservation, Set.of(resourceId), "telegram-user", "telegram",
                Optional.empty(), Optional.empty()));
        componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::replyAvailability)
            .invoke(new ReservationEntity.ReplyAvailability(reservationId, resourceId, true));
        componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::fulfill)
            .invoke(new ReservationEntity.Fulfill(resourceId, reservationId, reservation));

        // Give the consumer a moment to (not) act, then assert paymentId never gets recorded (SC-003).
        Thread.sleep(500);
        var state = componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::getReservation).invoke();
        assertThat(state.paymentId()).isEmpty();
    }

    @Test
    public void cancelBeforeCommitmentCutoff_neverCreatesPaymentEntity() throws Exception {
        String resourceId = "court-" + shortId();
        String reservationId = "reservation-" + shortId();
        // commitmentWindow far longer than how soon the slot starts means the cutoff hasn't arrived yet.
        LocalDateTime slotStart = LocalDateTime.now().plusDays(10);
        Reservation reservation = new Reservation(java.util.List.of("Carol"), slotStart);

        componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::create)
            .invoke(new Resource(resourceId, "Court 3", null));
        componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::setPricingPolicyOverride)
            .invoke(new PricingPolicy(5000, "eur", 0.10, Duration.ofDays(2)));

        componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::init)
            .invoke(new ReservationEntity.Init(reservation, Set.of(resourceId), "telegram-user", "telegram",
                Optional.empty(), Optional.empty()));
        componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::replyAvailability)
            .invoke(new ReservationEntity.ReplyAvailability(reservationId, resourceId, true));
        componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::fulfill)
            .invoke(new ReservationEntity.Fulfill(resourceId, reservationId, reservation));

        componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::cancelRequest).invoke();

        // recordPaymentId is only ever invoked once the commitment-cutoff Timer actually fires — with a
        // 2-day window and a 10-day-out slot, it never fires within this test's lifetime.
        Thread.sleep(500);
        var payment = componentClient.forEventSourcedEntity(reservationId).method(PaymentEntity::getPayment).invoke();
        assertThat(payment.state()).isEqualTo(PaymentState.State.NONE);
    }

    private <T> T eventually(CheckedSupplier<T> query, java.util.function.Predicate<T> until) throws Exception {
        T last = null;
        for (int i = 0; i < 100; i++) {
            last = query.get();
            if (until.test(last)) {
                return last;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met after 5s. Last value: " + last);
    }

    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
