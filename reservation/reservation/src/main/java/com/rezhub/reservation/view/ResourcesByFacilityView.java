package com.rezhub.reservation.view;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.rezhub.reservation.resource.ResourceEntity;
import com.rezhub.reservation.resource.ResourceEvent;

import java.util.List;

/**
 * Projects resource-to-facility associations.
 * Updated via ExternalRefSet (new provisioning path: POST /resource + PUT /external-ref)
 * and via FacilityResourceCreated (legacy path — kept for journal replay compatibility).
 */
@Component(id = "view_resources_by_facility")
public class ResourcesByFacilityView extends View {

    public record Row(String resourceId, String facilityId) {}
    public record Rows(List<Row> rows) {}

    @Consume.FromEventSourcedEntity(ResourceEntity.class)
    public static class ResourcesByFacilityUpdater extends TableUpdater<Row> {

        public Effect<Row> onEvent(ResourceEvent event) {
            return switch (event) {
                case ResourceEvent.FacilityResourceCreated e ->
                    e.parentId() != null && !e.parentId().isBlank()
                        ? effects().updateRow(new Row(e.resourceId(), e.parentId()))
                        : effects().ignore();
                case ResourceEvent.ExternalRefSet e ->
                    e.externalGroupRef() != null && !e.externalGroupRef().isBlank()
                        ? effects().updateRow(new Row(e.resourceId(), e.externalGroupRef()))
                        : effects().ignore();
                case ResourceEvent.ResourceCreated e -> effects().ignore();
                case ResourceEvent.AvalabilityChecked e -> effects().ignore();
                case ResourceEvent.ReservationAccepted e -> effects().ignore();
                case ResourceEvent.ReservationRejected e -> effects().ignore();
                case ResourceEvent.ReservationCanceled e -> effects().ignore();
                case ResourceEvent.WeeklyScheduleUpdated e -> effects().ignore();
                case ResourceEvent.ResourceTypeSet e -> effects().ignore();
            };
        }
    }

    @Query("SELECT * AS rows FROM resources_by_facility WHERE facilityId = :facilityId")
    public QueryEffect<Rows> getByFacilityId(String facilityId) {
        return queryResult();
    }
}
