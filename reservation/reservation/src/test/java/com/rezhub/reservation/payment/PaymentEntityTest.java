package com.rezhub.reservation.payment;

import akka.javasdk.testkit.EventSourcedTestKit;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

public class PaymentEntityTest {

    private static final String PAYMENT_ID = "payment-1";
    private static final String RESOURCE_ID = "court-1";
    private static final LocalDateTime SLOT = LocalDateTime.of(2026, 6, 1, 10, 0);

    @Test
    public void authorize_fromNone_succeeds() {
        var testKit = EventSourcedTestKit.of(PAYMENT_ID, PaymentEntity::new);

        var result = testKit.method(PaymentEntity::authorize)
            .invoke(new PaymentEntity.Authorize(PAYMENT_ID, RESOURCE_ID, SLOT, "pi_1", 5000, "eur", "acct_1", 500));

        assertThat(result.isError()).isFalse();
        assertThat(testKit.getState().state()).isEqualTo(PaymentState.State.AUTHORIZED);
        assertThat(testKit.getState().stripePaymentIntentId()).contains("pi_1");
    }

    @Test
    public void authorize_fromNonNoneState_isRejected() {
        var testKit = EventSourcedTestKit.of(PAYMENT_ID, PaymentEntity::new);
        testKit.method(PaymentEntity::authorize)
            .invoke(new PaymentEntity.Authorize(PAYMENT_ID, RESOURCE_ID, SLOT, "pi_1", 5000, "eur", "acct_1", 500));

        var result = testKit.method(PaymentEntity::authorize)
            .invoke(new PaymentEntity.Authorize(PAYMENT_ID, RESOURCE_ID, SLOT, "pi_2", 5000, "eur", "acct_1", 500));

        assertThat(result.isError()).isTrue();
    }

    @Test
    public void capture_fromAuthorized_succeeds() {
        var testKit = EventSourcedTestKit.of(PAYMENT_ID, PaymentEntity::new);
        testKit.method(PaymentEntity::authorize)
            .invoke(new PaymentEntity.Authorize(PAYMENT_ID, RESOURCE_ID, SLOT, "pi_1", 5000, "eur", "acct_1", 500));

        var result = testKit.method(PaymentEntity::capture).invoke(new PaymentEntity.Capture("ch_1"));

        assertThat(result.isError()).isFalse();
        assertThat(testKit.getState().state()).isEqualTo(PaymentState.State.CAPTURED);
        assertThat(testKit.getState().stripeChargeId()).contains("ch_1");
    }

    @Test
    public void capture_fromAnyOtherState_isRejected() {
        var testKit = EventSourcedTestKit.of(PAYMENT_ID, PaymentEntity::new);

        var result = testKit.method(PaymentEntity::capture).invoke(new PaymentEntity.Capture("ch_1"));

        assertThat(result.isError()).isTrue();
    }

    @Test
    public void fail_fromNone_succeeds() {
        var testKit = EventSourcedTestKit.of(PAYMENT_ID, PaymentEntity::new);

        var result = testKit.method(PaymentEntity::fail)
            .invoke(new PaymentEntity.Fail(RESOURCE_ID, SLOT, "card_declined"));

        assertThat(result.isError()).isFalse();
        assertThat(testKit.getState().state()).isEqualTo(PaymentState.State.FAILED);
        assertThat(testKit.getState().failureReason()).contains("card_declined");
    }

    @Test
    public void fail_fromAuthorized_succeeds() {
        var testKit = EventSourcedTestKit.of(PAYMENT_ID, PaymentEntity::new);
        testKit.method(PaymentEntity::authorize)
            .invoke(new PaymentEntity.Authorize(PAYMENT_ID, RESOURCE_ID, SLOT, "pi_1", 5000, "eur", "acct_1", 500));

        var result = testKit.method(PaymentEntity::fail)
            .invoke(new PaymentEntity.Fail(RESOURCE_ID, SLOT, "authentication_required"));

        assertThat(result.isError()).isFalse();
        assertThat(testKit.getState().state()).isEqualTo(PaymentState.State.FAILED);
    }

    @Test
    public void fail_fromCaptured_isRejected() {
        var testKit = EventSourcedTestKit.of(PAYMENT_ID, PaymentEntity::new);
        testKit.method(PaymentEntity::authorize)
            .invoke(new PaymentEntity.Authorize(PAYMENT_ID, RESOURCE_ID, SLOT, "pi_1", 5000, "eur", "acct_1", 500));
        testKit.method(PaymentEntity::capture).invoke(new PaymentEntity.Capture("ch_1"));

        var result = testKit.method(PaymentEntity::fail)
            .invoke(new PaymentEntity.Fail(RESOURCE_ID, SLOT, "too_late"));

        assertThat(result.isError()).isTrue();
        assertThat(testKit.getState().state()).isEqualTo(PaymentState.State.CAPTURED);
    }
}
