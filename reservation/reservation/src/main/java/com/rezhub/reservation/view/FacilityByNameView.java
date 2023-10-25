package com.rezhub.reservation.view;

import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.customer.facility.FacilityEvent;
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
        return effects().updateState(new FacilityV(
          created.facility().name(),
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
        return effects().ignore();
    }

    @SuppressWarnings("unused")
    @Subscribe.EventSourcedEntity(FacilityEntity.class)
    public UpdateEffect<FacilityV> onEvent(FacilityEvent.ResourceCreateAndRegisterRequested event) {
        return effects().ignore();
    }

    @SuppressWarnings("unused")
    @Subscribe.EventSourcedEntity(FacilityEntity.class)
    public UpdateEffect<FacilityV> onEvent(FacilityEvent.ResourceRegistered event) {
        return effects().ignore();
    }

    @SuppressWarnings("unused")
    @Subscribe.EventSourcedEntity(FacilityEntity.class)
    public UpdateEffect<FacilityV> onEvent(FacilityEvent.ResourceUnregistered event) {
        return effects().ignore();
    }

    @SuppressWarnings("unused")
    @Subscribe.EventSourcedEntity(FacilityEntity.class)
    public UpdateEffect<FacilityV> onEvent(FacilityEvent.AvalabilityRequested event) {
        return effects().ignore();
    }
}