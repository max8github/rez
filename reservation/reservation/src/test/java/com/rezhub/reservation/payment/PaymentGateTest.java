package com.rezhub.reservation.payment;

import akka.javasdk.testkit.TestKitSupport;
import com.rezhub.reservation.customer.dto.Address;
import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.infrastructure.StripeService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class PaymentGateTest extends TestKitSupport {

    private PaymentGate paymentGate() {
        return new PaymentGate(componentClient, new StripeService());
    }

    @Test
    public void isPlayerPayable_falseForAbsentIdentity() {
        assertThat(paymentGate().isPlayerPayable(Optional.empty())).isFalse();
    }

    @Test
    public void isPlayerPayable_falseForIdentityWithNoProfile() {
        assertThat(paymentGate().isPlayerPayable(Optional.of("unknown-user-" + shortId()))).isFalse();
    }

    @Test
    public void isPlayerPayable_trueOnceCardOnFile() {
        String userId = "user-" + shortId();
        componentClient.forKeyValueEntity(userId).method(PlayerPaymentProfileEntity::linkCustomer).invoke("cus_1");
        componentClient.forKeyValueEntity(userId).method(PlayerPaymentProfileEntity::setDefaultPaymentMethod).invoke("pm_1");

        assertThat(paymentGate().isPlayerPayable(Optional.of(userId))).isTrue();
    }

    @Test
    public void isFacilityPayable_trueWhenNoPricingPolicyConfigured() {
        String facilityId = "f_" + shortId();
        componentClient.forEventSourcedEntity(facilityId).method(FacilityEntity::create)
            .invoke(new Facility("Club", new Address("St", "City"), "Europe/Rome", null, null));

        assertThat(paymentGate().isFacilityPayable(facilityId)).isTrue();
    }

    @Test
    public void isFacilityPayable_falseWhenPricingPolicySetButNoConnectedAccount() {
        String facilityId = "f_" + shortId();
        componentClient.forEventSourcedEntity(facilityId).method(FacilityEntity::create)
            .invoke(new Facility("Club", new Address("St", "City"), "Europe/Rome", null, null));
        componentClient.forEventSourcedEntity(facilityId).method(FacilityEntity::setPricingPolicy)
            .invoke(new PricingPolicy(5000, "eur", 0.10, Duration.ofDays(2)));

        assertThat(paymentGate().isFacilityPayable(facilityId)).isFalse();
    }

    @Test
    public void isFacilityPayable_trueWhenPricingPolicyAndConnectedAccountBothSet() {
        String facilityId = "f_" + shortId();
        componentClient.forEventSourcedEntity(facilityId).method(FacilityEntity::create)
            .invoke(new Facility("Club", new Address("St", "City"), "Europe/Rome", null, null));
        componentClient.forEventSourcedEntity(facilityId).method(FacilityEntity::setPricingPolicy)
            .invoke(new PricingPolicy(5000, "eur", 0.10, Duration.ofDays(2)));
        componentClient.forEventSourcedEntity(facilityId).method(FacilityEntity::setStripeConnectedAccount)
            .invoke("acct_mock_1");

        // StripeService in no-op mode (no STRIPE_SECRET_KEY in test env) treats every account as charges-enabled.
        assertThat(paymentGate().isFacilityPayable(facilityId)).isTrue();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
