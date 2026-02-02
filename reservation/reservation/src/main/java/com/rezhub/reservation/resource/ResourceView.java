package com.rezhub.reservation.resource;

import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Subscribe;
import akka.javasdk.annotations.Table;
import akka.javasdk.annotations.ViewId;
import akka.javasdk.view.View;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Flux;

@ViewId("view_resources_by_container_id")
@Table("resources_by_container_id")
public class ResourceView extends View<ResourceV> {

    @SuppressWarnings("unused")
    @GetMapping("/resource/by_container/{container_id}")
    @Query("SELECT * FROM resources_by_container_id WHERE facilityId = :container_id")
    public Flux<ResourceV> getResource(String container_id) {
        return null;
    }

    @SuppressWarnings("unused")
    @Subscribe.EventSourcedEntity(ResourceEntity.class)
    public UpdateEffect<ResourceV> onEvent(ResourceEvent.FacilityResourceCreated created) {
        String id = updateContext().eventSubject().orElse("");
        assert id.equals(created.resourceId());
        return effects().updateState(ResourceV.initialize(created));
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
    public UpdateEffect<ResourceV> onEvent(ResourceEvent.AvalabilityChecked notInteresting) {
        return effects().ignore();
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
    public UpdateEffect<ResourceV> onEvent(ResourceEvent.ReservationCanceled event) {
        return effects().updateState(viewState().withoutBooking(event.dateTime()));
    }
}
