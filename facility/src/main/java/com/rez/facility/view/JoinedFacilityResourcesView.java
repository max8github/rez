package com.rez.facility.view;

import com.rez.facility.api.FacilityEntity;
import com.rez.facility.api.FacilityEvent;
import com.rez.facility.api.ResourceEntity;
import com.rez.facility.api.ResourceEvent;
import com.rez.facility.domain.Address;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;
import kalix.javasdk.view.View;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Flux;

@ViewId("joined-facility-resources")
public class JoinedFacilityResourcesView {

    @GetMapping("/joined_facility_resources/{facilityId}")
    @Query(
            """
            SELECT *
            FROM facilities
            JOIN resources ON facilities.facilityId = resources.facilityId
            WHERE facilities.facilityId = :facilityId
            ORDER BY resources.resourceName
            """)
    public Flux<FacilityResource> get(String facilityId) {
        return null;
    }

    @Table("facilities")
    @Subscribe.EventSourcedEntity(FacilityEntity.class)
    public static class Facilities extends View<FacilityVJ> {
        public UpdateEffect<FacilityVJ> onEvent(FacilityEvent.Created created) {
            String id = updateContext().eventSubject().orElse("");
            return effects()
                    .updateState(new FacilityVJ(id, created.facility().name(),
                            created.facility().address().toAddressState()));
        }

        public UpdateEffect<FacilityVJ> onEvent(FacilityEvent.Renamed event) {
            return effects().updateState(viewState().withName(event.newName()));
        }

        public UpdateEffect<FacilityVJ> onEvent(FacilityEvent.AddressChanged event) {
            return effects().updateState(viewState().withAddress(event.address().toAddressState()));
        }

        public View.UpdateEffect<FacilityVJ> onEvent(FacilityEvent.ResourceSubmitted event) {
            return effects().ignore();
        }

        public View.UpdateEffect<FacilityVJ> onEvent(FacilityEvent.ResourceIdAdded event) {
            return effects().updateState(viewState());
        }

        public View.UpdateEffect<FacilityVJ> onEvent(FacilityEvent.ResourceIdRemoved event) {
            return effects().updateState(viewState());
        }

        public View.UpdateEffect<FacilityVJ> onEvent(FacilityEvent.ReservationCreated event) {
            return effects().ignore();
        }
    }

    @Table("resources")
    @Subscribe.EventSourcedEntity(ResourceEntity.class)
    public static class Resources extends View<ResourceVJ> {

        public UpdateEffect<ResourceVJ> onEvent(ResourceEvent.ResourceCreated created) {
            String id = updateContext().eventSubject().orElse("");
            assert id.equals(created.entityId());
            return effects().updateState(new ResourceVJ(created.facilityId(),
                    created.entityId(), created.resource().resourceName()));
        }

        public UpdateEffect<ResourceVJ> onEvent(ResourceEvent.BookingAccepted event) {
            return effects().ignore();
        }

        public UpdateEffect<ResourceVJ> onEvent(ResourceEvent.BookingRejected notInteresting) {
            return effects().ignore();
        }
    }

    public record FacilityVJ(String facilityId, String name, Address address) {
        public FacilityVJ withName(String newName) {
            return new FacilityVJ(facilityId, newName, address);
        }
        public FacilityVJ withAddress(Address newAddress) {
            return new FacilityVJ(facilityId, name, newAddress);
        }
    }
    public record ResourceVJ(String facilityId, String resourceId, String resourceName) {}
}
