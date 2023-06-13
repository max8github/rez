package com.rez.facility.view;

import com.rez.facility.api.*;
import com.rez.facility.domain.Facility;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;
import kalix.javasdk.view.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Flux;

@ViewId("joined-facility-resources")
public class JoinedFacilityResourcesView {

    private static final Logger log = LoggerFactory.getLogger(JoinedFacilityResourcesView.class);

    @GetMapping("/joined-facility-resources/{facilityId}")
    @Query(
            """
            SELECT *
            FROM facilities
            JOIN resources ON facilities.facilityId = resources.facilityId
            WHERE facilities.facilityId = :facilityId
            ORDER BY resources.name
            """)
    public Flux<FacilityResource> get(String facilityId) {
        return null;
    }

    @Table("facilities")
    @Subscribe.EventSourcedEntity(FacilityEntity.class)
    public static class Facilities extends View<Facility> {

        public UpdateEffect<Facility> onEvent(FacilityEvent.ResourceSubmitted event) {
            return effects().ignore();
        }
        public UpdateEffect<Facility> onEvent(FacilityEvent.Created created) {
            String id = updateContext().eventSubject().orElse("");
            return effects()
                    .updateState(new Facility(id, created.facility().name(),
                            created.facility().address().toAddressState(), created.facility().resourceIds()));
        }

        public UpdateEffect<Facility> onEvent(FacilityEvent.ResourceIdAdded event) {
            return effects().updateState(viewState().withResourceId(event.resourceEntityId()));
        }

        public UpdateEffect<Facility> onEvent(FacilityEvent.ResourceIdRemoved event) {
            return effects().updateState(viewState().withoutResourceId(event.resourceEntityId()));
        }

        public UpdateEffect<Facility> onEvent(FacilityEvent.Renamed event) {
            return effects().updateState(viewState().withName(event.newName()));
        }

        public UpdateEffect<Facility> onEvent(FacilityEvent.AddressChanged event) {
            return effects().updateState(viewState().withAddress(event.address().toAddressState()));
        }

        public UpdateEffect<Facility> onEvent(FacilityEvent.ReservationCreated event) {
            return effects().ignore();
        }
    }

    @Table("resources")
    @Subscribe.EventSourcedEntity(ResourceEntity.class)
    public static class Resources extends View<Resource> {
        public UpdateEffect<Resource> onEvent(ResourceEvent.ResourceCreated created) {
            String id = updateContext().eventSubject().orElse("");
            return effects().updateState(Resource.initialize(created.facilityId(),
                    created.entityId(), created.resource().toResourceState()));
        }

        public UpdateEffect<Resource> onEvent(ResourceEvent.BookingAccepted event) {
            return effects().updateState(viewState().withTimeWindow(
                    event.reservation().timeSlot(), event.reservation().username()));
        }

        public UpdateEffect<Resource> onEvent(ResourceEvent.BookingRejected notInteresting) {
            return effects().ignore();
        }
    }
}
