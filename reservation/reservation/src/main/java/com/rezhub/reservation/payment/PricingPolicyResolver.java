package com.rezhub.reservation.payment;

import akka.javasdk.client.ComponentClient;
import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.customer.facility.FacilityState;
import com.rezhub.reservation.resource.ResourceEntity;
import com.rezhub.reservation.resource.ResourceState;

import java.util.Optional;

/**
 * FR-003's resolution rule (resource override, else facility default), shared by
 * {@code PaymentSchedulingAction} (reads it once, only for {@code commitmentWindow}, to schedule the
 * Timer) and {@code CommitmentCutoffTimedAction} (re-reads it fresh at fire time, per FR-013, to
 * determine the actual price/commission charged).
 */
final class PricingPolicyResolver {

    private PricingPolicyResolver() {}

    static Optional<PricingPolicy> resolve(ComponentClient componentClient, String resourceId) {
        ResourceState resourceState = componentClient.forEventSourcedEntity(resourceId)
            .method(ResourceEntity::getResource)
            .invoke();
        if (resourceState.pricingPolicyOverride().isPresent()) {
            return resourceState.pricingPolicyOverride();
        }
        String facilityId = resourceState.externalGroupRef();
        if (facilityId == null || facilityId.isBlank()) {
            return Optional.empty();
        }
        FacilityState facilityState = componentClient.forEventSourcedEntity(facilityId)
            .method(FacilityEntity::getState)
            .invoke();
        return facilityState.pricingPolicy();
    }

    static String resolveFacilityId(ComponentClient componentClient, String resourceId) {
        ResourceState resourceState = componentClient.forEventSourcedEntity(resourceId)
            .method(ResourceEntity::getResource)
            .invoke();
        return resourceState.externalGroupRef();
    }
}
