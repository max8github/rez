package com.rez.facility.view;

import com.rez.facility.pool.FacilityEntity;
import com.rez.facility.pool.FacilityEvent;
import com.rez.facility.pool.dto.Address;
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

    @SuppressWarnings("unused")
    @GetMapping("/facility/by_name/{facility_name}")
    @Query("SELECT * FROM facilities_by_name WHERE name = :facility_name")
    public Flux<FacilityV> getFacility(String name) {
        return null;
    }

    @SuppressWarnings("unused")
    @Subscribe.EventSourcedEntity(FacilityEntity.class)
    public UpdateEffect<FacilityV> onEvent(FacilityEvent.Created created) {
        Address address = created.facility().address();
        return effects().updateState(new FacilityV(
                created.facility().name(),
                new com.rez.facility.pool.Address(address.street(), address.city()),
                created.entityId()));
    }

    @SuppressWarnings("unused")
    @Subscribe.EventSourcedEntity(FacilityEntity.class)
    public UpdateEffect<FacilityV> onEvent(FacilityEvent.Renamed event) {
        return effects().updateState(viewState().withName(event.newName()));
    }

    @SuppressWarnings("unused")
    @Subscribe.EventSourcedEntity(FacilityEntity.class)
    public UpdateEffect<FacilityV> onEvent(FacilityEvent.AddressChanged event) {
        Address address = event.address();
        return effects().updateState(viewState().withAddress(new com.rez.facility.pool.Address(address.street(), address.city())));
    }

    @SuppressWarnings("unused")
    @Subscribe.EventSourcedEntity(FacilityEntity.class)
    public UpdateEffect<FacilityV> onEvent(FacilityEvent.ResourceSubmitted event) {
        return effects().ignore();
    }

    @SuppressWarnings("unused")
    @Subscribe.EventSourcedEntity(FacilityEntity.class)
    public UpdateEffect<FacilityV> onEvent(FacilityEvent.ResourceIdAdded event) {
        return effects().ignore();
    }

    @SuppressWarnings("unused")
    @Subscribe.EventSourcedEntity(FacilityEntity.class)
    public UpdateEffect<FacilityV> onEvent(FacilityEvent.ResourceIdRemoved event) {
        return effects().ignore();
    }

    @SuppressWarnings("unused")
    @Subscribe.EventSourcedEntity(FacilityEntity.class)
    public UpdateEffect<FacilityV> onEvent(FacilityEvent.ReservationCreated event) {
        return effects().ignore();
    }
}