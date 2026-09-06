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
        return resolveAll(componentClient, resourceId).policy();
    }

    static String resolveFacilityId(ComponentClient componentClient, String resourceId) {
        return resolveAll(componentClient, resourceId).facilityId();
    }

    /** Everything {@code CommitmentCutoffTimedAction} needs, behind a single {@code ResourceState}
     * fetch — calling {@link #resolve} and {@link #resolveFacilityId} separately (as before) each
     * re-fetches the same entity for overlapping data. */
    record Resolution(Optional<PricingPolicy> policy, String facilityId, String resourceName) {}

    static Resolution resolveAll(ComponentClient componentClient, String resourceId) {
        ResourceState resourceState = componentClient.forEventSourcedEntity(resourceId)
            .method(ResourceEntity::getResource)
            .invoke();
        String facilityId = resourceState.externalGroupRef();
        Optional<PricingPolicy> policy = resourceState.pricingPolicyOverride();
        if (policy.isEmpty() && facilityId != null && !facilityId.isBlank()) {
            FacilityState facilityState = componentClient.forEventSourcedEntity(facilityId)
                .method(FacilityEntity::getState)
                .invoke();
            policy = facilityState.pricingPolicy();
        }
        return new Resolution(policy, facilityId, resourceState.name());
    }
}
