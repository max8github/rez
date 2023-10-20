package com.rezhub.reservation.resource;

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

    @SuppressWarnings("unused")
    @GetMapping("/resource/by_facility/{facility_id}")
    @Query("SELECT * FROM resources_by_facility_id WHERE facilityId = :facility_id")
    public Flux<ResourceV> getResource(String facility_id) {
        return null;
    }

    @SuppressWarnings("unused")
    @Subscribe.EventSourcedEntity(ResourceEntity.class)
    public UpdateEffect<ResourceV> onEvent(ResourceEvent.ResourceCreated created) {
        String id = updateContext().eventSubject().orElse("");
        assert id.equals(created.resourceId());
        return effects().updateState(ResourceV.initialize(created));
    }

    @SuppressWarnings("unused")
    @Subscribe.EventSourcedEntity(ResourceEntity.class)
    public UpdateEffect<ResourceV> onEvent(ResourceEvent.ReservationAccepted event) {
        String reservationId = event.reservationId();
        return effects().updateState(viewState().withBooking(event.reservation().dateTime(), reservationId));
    }

    @SuppressWarnings("unused")
    @Subscribe.EventSourcedEntity(ResourceEntity.class)
    public UpdateEffect<ResourceV> onEvent(ResourceEvent.ReservationRejected notInteresting) {
        return effects().ignore();
    }


    @SuppressWarnings("unused")
    @Subscribe.EventSourcedEntity(ResourceEntity.class)
    public UpdateEffect<ResourceV> onEvent(ResourceEvent.AvalabilityChecked notInteresting) {
        return effects().ignore();
    }

    @SuppressWarnings("unused")
    @Subscribe.EventSourcedEntity(ResourceEntity.class)
    public UpdateEffect<ResourceV> onEvent(ResourceEvent.ReservationCanceled event) {
        return effects().updateState(viewState().withoutBooking(event.dateTime()));
    }
}
