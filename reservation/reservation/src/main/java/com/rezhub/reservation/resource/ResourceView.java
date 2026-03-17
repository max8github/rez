package com.rezhub.reservation.resource;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

import java.util.List;

@Component(id = "view_resources_by_container_id")
public class ResourceView extends View {

    public record Resources(List<ResourceV> resources) {}

    @Consume.FromEventSourcedEntity(ResourceEntity.class)
    public static class ResourcesByContainerUpdater extends TableUpdater<ResourceV> {

        public Effect<ResourceV> onEvent(ResourceEvent event) {
            String id = updateContext().eventSubject().orElse("");
            return switch (event) {
                case ResourceEvent.FacilityResourceCreated e -> {
                    assert id.equals(e.resourceId());
                    yield effects().updateRow(ResourceV.initialize(e));
                }
                case ResourceEvent.ResourceCreated e -> {
                    assert id.equals(e.resourceId());
                    yield effects().updateRow(ResourceV.initialize(e));
                }
                case ResourceEvent.AvalabilityChecked e -> effects().ignore();
                case ResourceEvent.ReservationAccepted e ->
                    rowState() == null ? effects().ignore() :
                    effects().updateRow(rowState().withBooking(e.reservation().dateTime(), e.reservationId()));
                case ResourceEvent.ReservationRejected e -> effects().ignore();
                case ResourceEvent.ReservationCanceled e ->
                    rowState() == null ? effects().ignore() :
                    effects().updateRow(rowState().withoutBooking(e.dateTime()));
            };
        }
    }

    @Query("SELECT * AS resources FROM resources_by_container_id WHERE facilityId = :container_id")
    public QueryEffect<Resources> getResource(String container_id) {
        return queryResult();
    }
}
