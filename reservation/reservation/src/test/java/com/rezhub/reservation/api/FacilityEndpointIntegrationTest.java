package com.rezhub.reservation.api;

import akka.javasdk.testkit.TestKitSupport;
import com.rezhub.reservation.customer.dto.Address;
import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.payment.PricingPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class FacilityEndpointIntegrationTest extends TestKitSupport {

    private String createFacility() {
        String facilityId = "f_" + shortId();
        componentClient.forEventSourcedEntity(facilityId).method(FacilityEntity::create)
            .invoke(new Facility("Club", new Address("St", "City"), "Europe/Rome", null, null));
        return facilityId;
    }

    @Test
    public void putPricingPolicy_storesPolicy() throws Exception {
        String facilityId = createFacility();
        var policy = new PricingPolicy(6000, "eur", 0.12, Duration.ofDays(1));

        var response = httpClient.PUT("/facility/" + facilityId + "/pricing-policy")
            .withRequestBody(policy)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        var state = componentClient.forEventSourcedEntity(facilityId).method(FacilityEntity::getState).invoke();
        assertThat(state.pricingPolicy()).contains(policy);
    }

    @Test
    public void putPricingPolicy_rejectsCommitmentWindowOverCap() throws Exception {
        String facilityId = createFacility();
        var invalidPolicy = new PricingPolicy(6000, "eur", 0.12, Duration.ofDays(30));

        var response = httpClient.PUT("/facility/" + facilityId + "/pricing-policy")
            .withRequestBody(invalidPolicy)
            .invoke();

        assertThat(response.status().isSuccess()).isFalse();
        var state = componentClient.forEventSourcedEntity(facilityId).method(FacilityEntity::getState).invoke();
        assertThat(state.pricingPolicy()).isEmpty();
    }

    @Test
    public void putStripeConnectedAccount_storesAccountId() throws Exception {
        String facilityId = createFacility();

        var response = httpClient.PUT("/facility/" + facilityId + "/stripe-connected-account")
            .withRequestBody(new FacilityEndpoint.StripeConnectedAccountRequest("acct_test_123"))
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        var state = componentClient.forEventSourcedEntity(facilityId).method(FacilityEntity::getState).invoke();
        assertThat(state.stripeConnectedAccountId()).contains("acct_test_123");
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
