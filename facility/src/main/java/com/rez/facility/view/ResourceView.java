package com.rez.facility.view;

import com.rez.facility.entities.ResourceEntity;
import com.rez.facility.events.ResourceEvent;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;
import kalix.javasdk.view.View;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Flux;

@ViewId("view_resources_by_facility_id")
@Table("resources_by_facility_id")
public class ResourceView extends View<ResourceV> {

    @GetMapping("/resource/by_facility/{facility_id}")
    @Query("SELECT * FROM resources_by_facility_id WHERE facilityId = :facility_id")
    public Flux<ResourceV> getResource(String facility_id) {
        return null;
    }

    @Subscribe.EventSourcedEntity(ResourceEntity.class)
    public UpdateEffect<ResourceV> onEvent(ResourceEvent.ResourceCreated created) {
        String id = updateContext().eventSubject().orElse("");
        assert id.equals(created.entityId());
        return effects().updateState(ResourceV.initialize(created));
    }

    @Subscribe.EventSourcedEntity(ResourceEntity.class)
    public UpdateEffect<ResourceV> onEvent(ResourceEvent.BookingAccepted event) {
        String reservationId = event.reservationId();
        return effects().updateState(viewState().withBooking(event.reservation().timeSlot(), reservationId));
    }

    @Subscribe.EventSourcedEntity(ResourceEntity.class)
    public UpdateEffect<ResourceV> onEvent(ResourceEvent.BookingRejected notInteresting) {
        return effects().ignore();
    }

    @Subscribe.EventSourcedEntity(ResourceEntity.class)
    public UpdateEffect<ResourceV> onEvent(ResourceEvent.BookingCanceled cancellation) {
        return effects().updateState(viewState().withoutBooking(cancellation.timeSlot()));
    }
}
