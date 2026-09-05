package com.rezhub.reservation.payment;

import akka.javasdk.testkit.KeyValueEntityTestKit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PlayerPaymentProfileEntityTest {

    private static final String USER_ID = "user-1";

    @Test
    public void hasPaymentMethod_falseBeforeEitherFieldSet() {
        var testKit = KeyValueEntityTestKit.of(USER_ID, PlayerPaymentProfileEntity::new);

        assertThat(testKit.getState().hasPaymentMethod()).isFalse();
    }

    @Test
    public void linkCustomer_alone_stillHasNoPaymentMethod() {
        var testKit = KeyValueEntityTestKit.of(USER_ID, PlayerPaymentProfileEntity::new);

        var result = testKit.method(PlayerPaymentProfileEntity::linkCustomer).invoke("cus_1");

        assertThat(result.isError()).isFalse();
        assertThat(testKit.getState().stripeCustomerId()).contains("cus_1");
        assertThat(testKit.getState().hasPaymentMethod()).isFalse();
    }

    @Test
    public void linkCustomerAndSetDefaultPaymentMethod_hasPaymentMethodBecomesTrue() {
        var testKit = KeyValueEntityTestKit.of(USER_ID, PlayerPaymentProfileEntity::new);

        testKit.method(PlayerPaymentProfileEntity::linkCustomer).invoke("cus_1");
        testKit.method(PlayerPaymentProfileEntity::setDefaultPaymentMethod).invoke("pm_1");

        assertThat(testKit.getState().hasPaymentMethod()).isTrue();
        assertThat(testKit.getState().defaultPaymentMethodId()).contains("pm_1");
    }
}
