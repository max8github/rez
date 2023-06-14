package com.rez.facility.view;

import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.ViewId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Flux;

@ViewId("joined-facility-resources")
public class JoinedFacilityResourcesView {

    private static final Logger log = LoggerFactory.getLogger(JoinedFacilityResourcesView.class);

    @GetMapping("/joined_facility_resources/{facilityId}")
    @Query(
            """
            SELECT *
            FROM facilities_by_name
            INNER JOIN resources_by_facility_id ON facilities_by_name.id = resources_by_facility_id.facilityId
            WHERE facilities_by_name.id = :facilityId
            ORDER BY resources_by_facility_id.name
            """)
    public Flux<FacilityResource> get(String facilityId) {
        return null;
    }





//    @Table("facilities_by_name_internal")
//    public class FacilityByNameView extends View<FacilityV> {
//
//        @Subscribe.EventSourcedEntity(FacilityEntity.class)
//        public UpdateEffect<FacilityV> onEvent(FacilityEvent.Created created) {
//            return effects().updateState(new FacilityV(
//                    created.facility().name(),
//                    created.facility().address().toAddressState(),
//                    created.entityId()));
//        }
//
//        @Subscribe.EventSourcedEntity(FacilityEntity.class)
//        public UpdateEffect<FacilityV> onEvent(FacilityEvent.Renamed event) {
//            return effects().updateState(viewState().withName(event.newName()));
//        }
//
//        @Subscribe.EventSourcedEntity(FacilityEntity.class)
//        public UpdateEffect<FacilityV> onEvent(FacilityEvent.AddressChanged event) {
//            return effects().updateState(viewState().withAddress(event.address().toAddressState()));
//        }
//
//        @Subscribe.EventSourcedEntity(FacilityEntity.class)
//        public UpdateEffect<FacilityV> onEvent(FacilityEvent.ResourceSubmitted event) {
//            return effects().ignore();
//        }
//
//        @Subscribe.EventSourcedEntity(FacilityEntity.class)
//        public UpdateEffect<FacilityV> onEvent(FacilityEvent.ResourceIdAdded event) {
//            return effects().ignore();
//        }
//
//        @Subscribe.EventSourcedEntity(FacilityEntity.class)
//        public UpdateEffect<FacilityV> onEvent(FacilityEvent.ResourceIdRemoved event) {
//            return effects().ignore();
//        }
//
//        @Subscribe.EventSourcedEntity(FacilityEntity.class)
//        public UpdateEffect<FacilityV> onEvent(FacilityEvent.ReservationCreated event) {
//            return effects().ignore();
//        }
//    }
//
//
//
//
//
//    @Table("resources_by_facility_id_internal")
//    public class ResourceView extends View<ResourceV> {
//
//        @Subscribe.EventSourcedEntity(ResourceEntity.class)
//        public UpdateEffect<ResourceV> onEvent(ResourceEvent.ResourceCreated created) {
//            String id = updateContext().eventSubject().orElse("");
//            assert id.equals(created.entityId());
//            return effects().updateState(ResourceV.initialize(created.facilityId(),
//                    created.entityId(), created.resource().toResourceState()));
//        }
//
//        @Subscribe.EventSourcedEntity(ResourceEntity.class)
//        public UpdateEffect<ResourceV> onEvent(ResourceEvent.BookingAccepted event) {
//            return effects().updateState(viewState().withBooking(event.reservation().timeSlot(), event.reservation().username()));
//        }
//
//        @Subscribe.EventSourcedEntity(ResourceEntity.class)
//        public UpdateEffect<ResourceV> onEvent(ResourceEvent.BookingRejected notInteresting) {
//            return effects().ignore();
//        }
//    }
}
