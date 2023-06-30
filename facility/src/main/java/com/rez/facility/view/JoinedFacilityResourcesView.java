package com.rez.facility.view;

import com.rez.facility.api.FacilityEntity;
import com.rez.facility.api.FacilityEvent;
import com.rez.facility.api.ResourceEntity;
import com.rez.facility.api.ResourceEvent;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;
import kalix.javasdk.view.View;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Flux;

/**
 * Note: for this JOIN to work, it must be enabled in docker-compose.yml.
 */
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
    public static class Facilities extends View<FacilityV> {
        public UpdateEffect<FacilityV> onEvent(FacilityEvent.Created created) {
            String id = updateContext().eventSubject().orElse("");
            return effects()
                    .updateState(new FacilityV(created.facility().name(), created.facility().address().toAddressState(), id
                    ));
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
            assert id.equals(created.entityId());
            return effects().updateState(ResourceV.initialize(created));
        }

        public UpdateEffect<ResourceV> onEvent(ResourceEvent.BookingAccepted event) {
            return effects().ignore();
        }

        public UpdateEffect<ResourceV> onEvent(ResourceEvent.BookingRejected notInteresting) {
            return effects().ignore();
        }
    }
}
