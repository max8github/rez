package com.rezhub.reservation.view;

import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.customer.facility.FacilityEvent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

import java.util.List;

@Component(id = "view_facilities_by_name")
public class FacilityByNameView extends View {

    public record Facilities(List<FacilityV> facilities) {}

    @Consume.FromEventSourcedEntity(FacilityEntity.class)
    public static class FacilitiesByNameUpdater extends TableUpdater<FacilityV> {

        public Effect<FacilityV> onEvent(FacilityEvent event) {
            return switch (event) {
                case FacilityEvent.Created e ->
                    effects().updateRow(new FacilityV(e.facility().name(), e.entityId()));
                case FacilityEvent.Renamed e ->
                    effects().updateRow(rowState().withName(e.newName()));
                case FacilityEvent.AddressChanged e -> effects().ignore();
                case FacilityEvent.BotTokenUpdated e -> effects().ignore();
                case FacilityEvent.ResourceCreateAndRegisterRequested e -> effects().ignore();
                case FacilityEvent.ResourceRegistered e -> effects().ignore();
                case FacilityEvent.ResourceUnregistered e -> effects().ignore();
                case FacilityEvent.AvalabilityRequested e -> effects().ignore();
            };
        }
    }

    @Query("SELECT * AS facilities FROM facilities_by_name WHERE name = :facility_name")
    public QueryEffect<Facilities> getFacility(String facility_name) {
        return queryResult();
    }
}
