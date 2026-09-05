package com.rezhub.reservation.api;

import akka.javasdk.testkit.TestKitSupport;
import com.rezhub.reservation.customer.dto.Address;
import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.payment.PricingPolicy;
import com.rezhub.reservation.resource.ResourceEntity;
import com.rezhub.reservation.resource.dto.Resource;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceEndpointIntegrationTest extends TestKitSupport {

    @Test
    public void putPricingPolicy_storesOverride() throws Exception {
        String resourceId = "court-" + shortId();
        componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::create)
            .invoke(new Resource(resourceId, "Court 1", null));
        var policy = new PricingPolicy(8000, "eur", 0.15, Duration.ofDays(1));

        var response = httpClient.PUT("/resource/" + resourceId + "/pricing-policy")
            .withRequestBody(policy)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        var state = componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::getResource).invoke();
        assertThat(state.pricingPolicyOverride()).contains(policy);
    }

    /**
     * The actual precedence resolution (override wins over facility default) is exercised end-to-end
     * by CommitmentCutoffTimedActionTest's booking flow; this test verifies the endpoint's own
     * contribution — the override is set on the resource independently of the facility's default,
     * with both stored distinctly and retrievable via their respective GETs.
     */
    @Test
    public void resourceOverride_isStoredIndependentlyOfFacilityDefault() throws Exception {
        String facilityId = "f_" + shortId();
        String resourceId = "court-" + shortId();
        componentClient.forEventSourcedEntity(facilityId).method(FacilityEntity::create)
            .invoke(new Facility("Club", new Address("St", "City"), "Europe/Rome", null, null));
        var facilityDefault = new PricingPolicy(5000, "eur", 0.10, Duration.ofDays(1));
        componentClient.forEventSourcedEntity(facilityId).method(FacilityEntity::setPricingPolicy)
            .invoke(facilityDefault);
        componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::create)
            .invoke(new Resource(resourceId, "Court 1", null));
        componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::setExternalRef)
            .invoke(new ResourceEntity.SetExternalRef(resourceId, facilityId));

        var override = new PricingPolicy(9900, "eur", 0.20, Duration.ofDays(1));
        var response = httpClient.PUT("/resource/" + resourceId + "/pricing-policy")
            .withRequestBody(override)
            .invoke();
        assertThat(response.status().isSuccess()).isTrue();

        var resourceState = componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::getResource).invoke();
        var facilityState = componentClient.forEventSourcedEntity(facilityId).method(FacilityEntity::getState).invoke();
        assertThat(resourceState.pricingPolicyOverride()).contains(override);
        assertThat(facilityState.pricingPolicy()).contains(facilityDefault);
        assertThat(resourceState.pricingPolicyOverride()).isNotEqualTo(facilityState.pricingPolicy());
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
