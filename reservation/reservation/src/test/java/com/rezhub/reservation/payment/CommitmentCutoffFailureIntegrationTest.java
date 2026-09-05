package com.rezhub.reservation.payment;

import akka.javasdk.DependencyProvider;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import com.rezhub.reservation.customer.dto.Address;
import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.infrastructure.StripeService;
import com.rezhub.reservation.orchestration.BookingApplicationService;
import com.rezhub.reservation.orchestration.BookingContextResolverAkka;
import com.rezhub.reservation.orchestration.CourtBookingWorkflow;
import com.rezhub.reservation.orchestration.CourtDirectoryAkka;
import com.rezhub.reservation.orchestration.ReservationGatewayAkka;
import com.rezhub.reservation.reservation.ReservationEntity;
import com.rezhub.reservation.resource.ResourceEntity;
import com.rezhub.reservation.resource.dto.Resource;
import com.rezhub.reservation.spi.NotificationSender;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.CardException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User Story 4 (T050-T052) — FR-016's retry-vs-notify classification and FR-010's notify/grace-window/
 * cancel path. Uses a custom DependencyProvider (mirroring Bootstrap's real wiring) to substitute a
 * StripeService subclass that fails deterministically, since this SDK has no in-process mocking for
 * concrete infrastructure classes — same "override a single method" pattern already used elsewhere in
 * this codebase (e.g. StripeWebhookEndpointIntegrationTest's fixed webhook secret).
 */
public class CommitmentCutoffFailureIntegrationTest extends TestKitSupport {

    /**
     * Fails every createAndConfirmHold call with whichever exception was configured for that specific
     * reservationId (the idempotencyKey parameter) — not one shared global field. This class's two
     * tests share one runtime, and attemptHold fires asynchronously (via a Timer) well after
     * bookReservation() returns; a single shared "current exception" field would be a real race if one
     * test's setup overwrote it before the other test's own async attempt actually executed. Keying by
     * reservationId (fixed at book time, before either test's Timer can fire) removes that race
     * entirely rather than just narrowing its window.
     */
    static class FailingStripeService extends StripeService {
        final java.util.concurrent.ConcurrentHashMap<String, AtomicInteger> callCountsByReservation = new java.util.concurrent.ConcurrentHashMap<>();
        final java.util.concurrent.ConcurrentHashMap<String, java.util.function.Supplier<Exception>> exceptionsByReservation = new java.util.concurrent.ConcurrentHashMap<>();

        void failReservationWith(String reservationId, java.util.function.Supplier<Exception> exceptionSupplier) {
            exceptionsByReservation.put(reservationId, exceptionSupplier);
        }

        @Override
        public HoldResult createAndConfirmHold(long amountCents, String currency, String customerId,
                                               String paymentMethodId, String idempotencyKey) throws com.stripe.exception.StripeException {
            callCountsByReservation.computeIfAbsent(idempotencyKey, r -> new AtomicInteger(0)).incrementAndGet();
            Exception e = exceptionsByReservation.get(idempotencyKey).get();
            if (e instanceof com.stripe.exception.StripeException se) {
                throw se;
            }
            throw new RuntimeException(e);
        }

        int callCountFor(String reservationId) {
            AtomicInteger count = callCountsByReservation.get(reservationId);
            return count == null ? 0 : count.get();
        }
    }

    /**
     * Records every message sent per recipient. Per-recipient (not one shared global counter) so the
     * two tests in this class — sharing this static instance across one TestKit-managed runtime —
     * can't spuriously observe each other's async notification delivery.
     *
     * <p>Crucially, this records the message <em>text</em>, not just a count: {@code
     * DelegatingServiceAction} also sends a routine "your court is booked" confirmation on every
     * {@code Fulfilled} event, through this exact same {@code NotificationSender} — completely
     * independent of payment outcome. A bare send-count would conflate that unrelated confirmation
     * with the payment-failure notification these tests actually care about, since both land in the
     * same recipient's bucket. Tests must therefore assert on content (e.g. "mentions payment"), not
     * on "was anything sent."
     */
    static class RecordingNotificationSender implements NotificationSender {
        final java.util.concurrent.ConcurrentHashMap<String, java.util.List<String>> messagesByRecipient = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public CompletableFuture<String> send(String recipientId, String text) {
            messagesByRecipient.computeIfAbsent(recipientId, r -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(text);
            return CompletableFuture.completedFuture("ok");
        }

        java.util.List<String> messagesFor(String recipientId) {
            return messagesByRecipient.getOrDefault(recipientId, java.util.List.of());
        }

        boolean hasPaymentFailureMessage(String recipientId) {
            return messagesFor(recipientId).stream().anyMatch(m -> m.toLowerCase(java.util.Locale.ROOT).contains("payment"));
        }
    }

    private static final FailingStripeService FAILING_STRIPE_SERVICE = new FailingStripeService();
    private static final RecordingNotificationSender RECORDING_NOTIFIER = new RecordingNotificationSender();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT.withDependencyProvider(new DependencyProvider() {
            @Override
            public <T> T getDependency(Class<T> clazz) {
                ComponentClient cc = componentClient;
                if (clazz == StripeService.class) {
                    return clazz.cast(FAILING_STRIPE_SERVICE);
                } else if (clazz == NotificationSender.class) {
                    return clazz.cast(RECORDING_NOTIFIER);
                } else if (clazz == BookingApplicationService.class) {
                    var reservationGateway = new ReservationGatewayAkka(cc);
                    var courtDirectory = new CourtDirectoryAkka(cc);
                    var contextResolver = new BookingContextResolverAkka(cc);
                    var paymentGate = new PaymentGate(cc, FAILING_STRIPE_SERVICE);
                    var courtWorkflow = new CourtBookingWorkflow(courtDirectory, reservationGateway, cc, paymentGate, FAILING_STRIPE_SERVICE);
                    return clazz.cast(new BookingApplicationService(contextResolver, courtWorkflow));
                } else if (clazz == ReservationGatewayAkka.class) {
                    return clazz.cast(new ReservationGatewayAkka(cc));
                }
                throw new RuntimeException("No dependency registered for: " + clazz);
            }
        });
    }

    private void bookReservation(String reservationId, LocalDateTime slotStart, Duration commitmentWindow, String recipientId) {
        String userId = "user-" + shortId();
        String facilityId = "f_" + shortId();
        String resourceId = "court-" + shortId();
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
        componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::setPricingPolicyOverride)
            .invoke(new PricingPolicy(5000, "eur", 0.10, commitmentWindow));

        componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::init)
            .invoke(new ReservationEntity.Init(reservation, Set.of(resourceId), recipientId, "telegram",
                Optional.of(userId), Optional.empty()));
        componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::replyAvailability)
            .invoke(new ReservationEntity.ReplyAvailability(reservationId, resourceId, true));
        componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::fulfill)
            .invoke(new ReservationEntity.Fulfill(resourceId, reservationId, reservation));
    }

    @Test
    public void transientFailure_retriesAutomaticallyWithNoNotification() throws Exception {
        String reservationId = "reservation-" + shortId();
        String recipientId = "telegram-user-" + shortId();
        // Registered against this specific reservationId *before* booking, so there is no window in
        // which the async commitment-cutoff Timer could fire before the exception is configured.
        FAILING_STRIPE_SERVICE.failReservationWith(reservationId, () -> new ApiConnectionException("network blip"));

        bookReservation(reservationId, LocalDateTime.now().plusHours(1), Duration.ofDays(1), recipientId);

        eventually(() -> FAILING_STRIPE_SERVICE.callCountFor(reservationId), n -> n >= 2);

        // DelegatingServiceAction sends a routine booking-confirmation message on every Fulfilled
        // event through this same NotificationSender — asserting content, not a bare count, is what
        // actually isolates "no payment-failure notification was sent" (see RecordingNotificationSender's doc).
        assertThat(RECORDING_NOTIFIER.hasPaymentFailureMessage(recipientId)).isFalse();
        var payment = componentClient.forEventSourcedEntity(reservationId).method(PaymentEntity::getPayment).invoke();
        assertThat(payment.state()).isEqualTo(PaymentState.State.NONE);
    }

    @Test
    public void cardSpecificFailure_notifiesPlayerAndOpensGraceWindow() throws Exception {
        String reservationId = "reservation-" + shortId();
        String recipientId = "telegram-user-" + shortId();
        FAILING_STRIPE_SERVICE.failReservationWith(reservationId, () -> new CardException(
            "card declined", "req_1", "card_declined", null, null, null, 402, null));

        bookReservation(reservationId, LocalDateTime.now().plusHours(1), Duration.ofDays(1), recipientId);

        eventually(() -> RECORDING_NOTIFIER.hasPaymentFailureMessage(recipientId), has -> has);
        // No automatic retry for a card-specific failure — exactly one attempt.
        assertThat(FAILING_STRIPE_SERVICE.callCountFor(reservationId)).isEqualTo(1);
    }

    @Test
    public void graceWindowExpiry_withNoSuccessfulHold_cancelsReservationAndFailsPayment() throws Exception {
        String reservationId = "reservation-" + shortId();
        String recipientId = "telegram-user-" + shortId();
        FAILING_STRIPE_SERVICE.failReservationWith(reservationId, () -> new CardException(
            "card declined", "req_1", "card_declined", null, null, null, 402, null));
        // A slot only 3s out means GRACE_WINDOW (30m) clamps to ~3s (grace <= time-until-resolution),
        // so this test doesn't need to wait anywhere near the production default.
        LocalDateTime slotStart = LocalDateTime.now().plusSeconds(3);

        bookReservation(reservationId, slotStart, Duration.ofDays(1), recipientId);

        eventually(() -> RECORDING_NOTIFIER.hasPaymentFailureMessage(recipientId), has -> has);
        var beforeExpiry = componentClient.forEventSourcedEntity(reservationId).method(PaymentEntity::getPayment).invoke();
        assertThat(beforeExpiry.state()).isEqualTo(PaymentState.State.NONE); // not yet failed — still within the grace window

        var payment = eventually(() ->
                componentClient.forEventSourcedEntity(reservationId).method(PaymentEntity::getPayment).invoke(),
            p -> p.state() == PaymentState.State.FAILED);
        assertThat(payment.failureReason()).isPresent();

        // fail() and cancelRequest() are two separate calls in onGraceWindowExpired — cancellation can
        // land a moment after the payment state above, so this needs its own eventually() too.
        var reservation = eventually(() ->
                componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::getReservation).invoke(),
            r -> r.state() == com.rezhub.reservation.reservation.ReservationState.State.CANCELLED);
        assertThat(reservation.state()).isEqualTo(com.rezhub.reservation.reservation.ReservationState.State.CANCELLED);
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
