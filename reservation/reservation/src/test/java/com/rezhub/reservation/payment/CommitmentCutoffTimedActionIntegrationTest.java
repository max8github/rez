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

public class CommitmentCutoffTimedActionIntegrationTest extends TestKitSupport {

    /** Books a fully-payable reservation (facility onboarded, player has a card on file) and returns its id. */
    private String bookPayableReservation(LocalDateTime slotStart, PricingPolicy policy) {
        String userId = "user-" + shortId();
        String facilityId = "f_" + shortId();
        String resourceId = "court-" + shortId();
        String reservationId = "reservation-" + shortId();
        Reservation reservation = new Reservation(java.util.List.of("Alice"), slotStart);

        componentClient.forKeyValueEntity(userId).method(PlayerPaymentProfileEntity::linkCustomer).invoke("cus_1");
        componentClient.forKeyValueEntity(userId).method(PlayerPaymentProfileEntity::setDefaultPaymentMethod).invoke("pm_1");

        componentClient.forEventSourcedEntity(facilityId).method(FacilityEntity::create)
            .invoke(new Facility("Club", new Address("St", "City"), "Europe/Rome", null, null));
        componentClient.forEventSourcedEntity(facilityId).method(FacilityEntity::setStripeConnectedAccount).invoke("acct_1");
        componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::create)
            .invoke(new Resource(resourceId, "Court 1", null));
        componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::setExternalRef)
            .invoke(new ResourceEntity.SetExternalRef(resourceId, facilityId));
        componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::setPricingPolicyOverride).invoke(policy);

        componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::init)
            .invoke(new ReservationEntity.Init(reservation, Set.of(resourceId), "telegram-user", "telegram",
                Optional.of(userId), Optional.empty()));
        componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::replyAvailability)
            .invoke(new ReservationEntity.ReplyAvailability(reservationId, resourceId, true));
        componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::fulfill)
            .invoke(new ReservationEntity.Fulfill(resourceId, reservationId, reservation));

        return reservationId;
    }

    @Test
    public void attemptHold_happyPath_authorizesAndSchedulesCapture() throws Exception {
        // commitmentWindow far longer than "1 hour out" means the cutoff clamps to "now" -> fires immediately.
        String reservationId = bookPayableReservation(
            LocalDateTime.now().plusHours(1), new PricingPolicy(5000, "eur", 0.10, Duration.ofDays(1)));

        var payment = eventually(() ->
                componentClient.forEventSourcedEntity(reservationId).method(PaymentEntity::getPayment).invoke(),
            p -> p.state() == PaymentState.State.AUTHORIZED);

        assertThat(payment.stripePaymentIntentId()).isPresent();
        assertThat(payment.amountCents()).isEqualTo(5000);
        assertThat(payment.applicationFeeCents()).isEqualTo(500);
    }

    @Test
    public void resolutionPoint_capturesAuthorizedHold() throws Exception {
        // slotStart 2s out: attemptHold fires immediately (cutoff clamps to now), then the resolution-point
        // Timer (scheduled for slotStart) fires ~2s later — long enough to observe AUTHORIZED first.
        String reservationId = bookPayableReservation(
            LocalDateTime.now().plusSeconds(2), new PricingPolicy(5000, "eur", 0.10, Duration.ofDays(1)));

        eventually(() ->
                componentClient.forEventSourcedEntity(reservationId).method(PaymentEntity::getPayment).invoke(),
            p -> p.state() == PaymentState.State.AUTHORIZED);

        var captured = eventually(() ->
                componentClient.forEventSourcedEntity(reservationId).method(PaymentEntity::getPayment).invoke(),
            p -> p.state() == PaymentState.State.CAPTURED);

        assertThat(captured.stripeChargeId()).isPresent();
    }

    @Test
    public void policyChangedAfterFulfillment_usesFreshPolicyAtCommitmentCutoff() throws Exception {
        String userId = "user-" + shortId();
        String facilityId = "f_" + shortId();
        String resourceId = "court-" + shortId();
        String reservationId = "reservation-" + shortId();
        LocalDateTime slotStart = LocalDateTime.now().plusSeconds(4);
        Reservation reservation = new Reservation(java.util.List.of("Dana"), slotStart);

        componentClient.forKeyValueEntity(userId).method(PlayerPaymentProfileEntity::linkCustomer).invoke("cus_2");
        componentClient.forKeyValueEntity(userId).method(PlayerPaymentProfileEntity::setDefaultPaymentMethod).invoke("pm_2");
        componentClient.forEventSourcedEntity(facilityId).method(FacilityEntity::create)
            .invoke(new Facility("Club", new Address("St", "City"), "Europe/Rome", null, null));
        componentClient.forEventSourcedEntity(facilityId).method(FacilityEntity::setStripeConnectedAccount).invoke("acct_2");
        componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::create)
            .invoke(new Resource(resourceId, "Court 1", null));
        componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::setExternalRef)
            .invoke(new ResourceEntity.SetExternalRef(resourceId, facilityId));
        // Original policy in effect at booking/fulfillment time — commitmentWindow of 2s against a
        // slot 4s out leaves a real ~2s window before the commitment-cutoff Timer fires, wide enough to
        // reliably change the policy in between (see below) without racing it.
        componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::setPricingPolicyOverride)
            .invoke(new PricingPolicy(5000, "eur", 0.10, Duration.ofSeconds(2)));

        componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::init)
            .invoke(new ReservationEntity.Init(reservation, Set.of(resourceId), "telegram-user", "telegram",
                Optional.of(userId), Optional.empty()));
        componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::replyAvailability)
            .invoke(new ReservationEntity.ReplyAvailability(reservationId, resourceId, true));
        componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::fulfill)
            .invoke(new ReservationEntity.Fulfill(resourceId, reservationId, reservation));

        // Wait for paymentId to be recorded (proves PaymentSchedulingAction already read the *old* policy
        // for scheduling), then change the policy before the commitment-cutoff Timer actually fires.
        eventually(() ->
                componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::getReservation).invoke(),
            s -> s.paymentId().isPresent());
        componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::setPricingPolicyOverride)
            .invoke(new PricingPolicy(9900, "eur", 0.20, Duration.ofDays(1)));

        var payment = eventually(() ->
                componentClient.forEventSourcedEntity(reservationId).method(PaymentEntity::getPayment).invoke(),
            p -> p.state() == PaymentState.State.AUTHORIZED);

        assertThat(payment.amountCents()).isEqualTo(9900);
        assertThat(payment.applicationFeeCents()).isEqualTo(1980);
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
