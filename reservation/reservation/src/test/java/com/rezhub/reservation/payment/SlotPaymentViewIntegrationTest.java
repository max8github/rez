package com.rezhub.reservation.payment;

import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class SlotPaymentViewIntegrationTest extends TestKitSupport {

    @Test
    public void authorizedHold_becomesQueryableBySlot() throws Exception {
        String paymentId = "payment-" + shortId();
        String resourceId = "court-" + shortId();
        LocalDateTime slot = LocalDateTime.of(2026, 5, 1, 18, 0);

        componentClient.forEventSourcedEntity(paymentId)
            .method(PaymentEntity::authorize)
            .invoke(new PaymentEntity.Authorize(paymentId, resourceId, slot, "pi_test", 5000, "eur", "acct_test", 500));

        var result = eventually(() ->
                componentClient.forView()
                    .method(SlotPaymentView::getBySlot)
                    .invoke(new SlotPaymentView.SlotKey(resourceId, slot.toString())),
            Optional::isPresent);

        assertThat(result).isPresent().get().satisfies(entry -> {
            assertThat(entry.paymentId()).isEqualTo(paymentId);
            assertThat(entry.state()).isEqualTo(PaymentState.State.AUTHORIZED.name());
        });
    }

    @Test
    public void capturedHold_updatesExistingRowState() throws Exception {
        String paymentId = "payment-" + shortId();
        String resourceId = "court-" + shortId();
        LocalDateTime slot = LocalDateTime.of(2026, 5, 2, 9, 0);

        componentClient.forEventSourcedEntity(paymentId)
            .method(PaymentEntity::authorize)
            .invoke(new PaymentEntity.Authorize(paymentId, resourceId, slot, "pi_test", 5000, "eur", "acct_test", 500));
        componentClient.forEventSourcedEntity(paymentId)
            .method(PaymentEntity::capture)
            .invoke(new PaymentEntity.Capture("ch_test"));

        var result = eventually(() ->
                componentClient.forView()
                    .method(SlotPaymentView::getBySlot)
                    .invoke(new SlotPaymentView.SlotKey(resourceId, slot.toString())),
            entry -> entry.isPresent() && entry.get().state().equals(PaymentState.State.CAPTURED.name()));

        assertThat(result).isPresent().get().satisfies(entry ->
            assertThat(entry.state()).isEqualTo(PaymentState.State.CAPTURED.name()));
    }

    @Test
    public void failedHold_neverAuthorized_stillAppearsInView() throws Exception {
        String paymentId = "payment-" + shortId();
        String resourceId = "court-" + shortId();
        LocalDateTime slot = LocalDateTime.of(2026, 5, 3, 20, 0);

        componentClient.forEventSourcedEntity(paymentId)
            .method(PaymentEntity::fail)
            .invoke(new PaymentEntity.Fail(resourceId, slot, "card_declined"));

        var result = eventually(() ->
                componentClient.forView()
                    .method(SlotPaymentView::getBySlot)
                    .invoke(new SlotPaymentView.SlotKey(resourceId, slot.toString())),
            Optional::isPresent);

        assertThat(result).isPresent().get().satisfies(entry ->
            assertThat(entry.state()).isEqualTo(PaymentState.State.FAILED.name()));
    }

    private <T> T eventually(CheckedSupplier<T> query, java.util.function.Predicate<T> until) throws Exception {
        T last = null;
        for (int i = 0; i < 80; i++) {
            last = query.get();
            if (until.test(last)) {
                return last;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met after 4s. Last value: " + last);
    }

    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
