package com.rez.facility.view;

import com.rez.facility.api.*;
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
            JOIN resources ON facilities.id = resources.facilityId
            WHERE facilities.id = :facilityId
            """)
    public Flux<FacilityResource> get(String facilityId) {
        return null;
    }

    @Table("facilities")
    @Subscribe.EventSourcedEntity(FacilityEntity.class)
    public static class Facilities extends View<FacilityV> {
        public UpdateEffect<FacilityV> onEvent(FacilityEvent.Created created) {
            String id = updateContext().eventSubject().orElse("");
            return effects()
                    .updateState(new FacilityV(created.facility().name(),
                            created.facility().address().toAddressState(), id));
        }

        public UpdateEffect<FacilityV> onEvent(FacilityEvent.Renamed event) {
            return effects().updateState(viewState().withName(event.newName()));
        }

        public UpdateEffect<FacilityV> onEvent(FacilityEvent.AddressChanged event) {
            return effects().updateState(viewState().withAddress(event.address().toAddressState()));
        }

        public View.UpdateEffect<FacilityV> onEvent(FacilityEvent.ResourceSubmitted event) {
            return effects().ignore();
        }

        public View.UpdateEffect<FacilityV> onEvent(FacilityEvent.ResourceIdAdded event) {
            return effects().updateState(viewState());
        }

        public View.UpdateEffect<FacilityV> onEvent(FacilityEvent.ResourceIdRemoved event) {
            return effects().updateState(viewState());
        }

        public View.UpdateEffect<FacilityV> onEvent(FacilityEvent.ReservationCreated event) {
            return effects().ignore();
        }
    }

    @Table("resources")
    @Subscribe.EventSourcedEntity(ResourceEntity.class)
    public static class Resources extends View<ResourceV> {
        public UpdateEffect<ResourceV> onEvent(ResourceEvent.ResourceCreated created) {
            String id = updateContext().eventSubject().orElse("");
            return effects().updateState(ResourceV.initialize(created.facilityId(),
                    created.entityId(), created.resource().toResourceState()));
        }

        public UpdateEffect<ResourceV> onEvent(ResourceEvent.BookingAccepted event) {
            return effects().ignore();
//                    .updateState(viewState().withTimeWindow(
//                    event.reservation().timeSlot(), event.reservation().username()));
        }

        public UpdateEffect<ResourceV> onEvent(ResourceEvent.BookingRejected notInteresting) {
            return effects().ignore();
        }
    }
}
