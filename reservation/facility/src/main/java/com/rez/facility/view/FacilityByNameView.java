package com.rez.facility.view;

import com.rez.facility.entities.FacilityEntity;
import com.rez.facility.events.FacilityEvent;
import kalix.javasdk.view.View;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;
import reactor.core.publisher.Flux;

import org.springframework.web.bind.annotation.GetMapping;

@ViewId("view_facilities_by_name")
@Table("facilities_by_name")
public class FacilityByNameView extends View<FacilityV> {

    @GetMapping("/facility/by_name/{facility_name}")
    @Query("SELECT * FROM facilities_by_name WHERE name = :facility_name")
    public Flux<FacilityV> getFacility(String name) {
        return null;
    }

    @Subscribe.EventSourcedEntity(FacilityEntity.class)
    public UpdateEffect<FacilityV> onEvent(FacilityEvent.Created created) {
        return effects().updateState(new FacilityV(
                created.facility().name(),
                created.facility().address().toAddressState(),
                created.entityId()));
    }

    @Subscribe.EventSourcedEntity(FacilityEntity.class)
    public UpdateEffect<FacilityV> onEvent(FacilityEvent.Renamed event) {
        return effects().updateState(viewState().withName(event.newName()));
    }

    @Subscribe.EventSourcedEntity(FacilityEntity.class)
    public UpdateEffect<FacilityV> onEvent(FacilityEvent.AddressChanged event) {
        return effects().updateState(viewState().withAddress(event.address().toAddressState()));
    }

    @Subscribe.EventSourcedEntity(FacilityEntity.class)
    public UpdateEffect<FacilityV> onEvent(FacilityEvent.ResourceSubmitted event) {
        return effects().ignore();
    }

    @Subscribe.EventSourcedEntity(FacilityEntity.class)
    public UpdateEffect<FacilityV> onEvent(FacilityEvent.ResourceIdAdded event) {
        return effects().ignore();
    }

    @Subscribe.EventSourcedEntity(FacilityEntity.class)
    public UpdateEffect<FacilityV> onEvent(FacilityEvent.ResourceIdRemoved event) {
        return effects().ignore();
    }

    @Subscribe.EventSourcedEntity(FacilityEntity.class)
    public UpdateEffect<FacilityV> onEvent(FacilityEvent.ReservationCreated event) {
        return effects().ignore();
    }
}