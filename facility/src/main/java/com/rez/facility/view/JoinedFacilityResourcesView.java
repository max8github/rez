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
            FROM resources
            INNER JOIN facilities_by_name ON facilities_by_name.id = resources.facilityId
            """)
    public Flux<FacilityResource> get(String facilityId) {
        return null;
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
            return effects().ignore();
//                    .updateState(viewState().withTimeWindow(
//                    event.reservation().timeSlot(), event.reservation().username()));
        }

        public UpdateEffect<Resource> onEvent(ResourceEvent.BookingRejected notInteresting) {
            return effects().ignore();
        }
    }
}
