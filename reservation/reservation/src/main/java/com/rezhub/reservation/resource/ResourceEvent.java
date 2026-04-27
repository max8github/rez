package com.rezhub.reservation.resource;

import com.rezhub.reservation.dto.Reservation;
import akka.javasdk.annotations.TypeName;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

public sealed interface ResourceEvent {

    @TypeName("facility-resource-created")
    record FacilityResourceCreated(String resourceId, String name, String parentId, String calendarId) implements ResourceEvent {}
    @TypeName("resource-created")
    record ResourceCreated(String resourceId, String resourceName, String calendarId) implements ResourceEvent {}
    @TypeName("availability-checked")
    record AvalabilityChecked(String resourceId, String reservationId, boolean available) implements ResourceEvent {}
    @TypeName("reservation-accepted")
    record ReservationAccepted(String resourceId, String reservationId, Reservation reservation) implements ResourceEvent {}
    @TypeName("reservation-rejected")
    record ReservationRejected(String resourceId, String reservationId, Reservation reservation) implements ResourceEvent {}
    @TypeName("reservation-canceled")
    record ReservationCanceled(String resourceId, String reservationId, LocalDateTime dateTime) implements ResourceEvent {}

    @TypeName("weekly-schedule-updated")
    record WeeklyScheduleUpdated(String resourceId, Map<DayOfWeek, Set<LocalTime>> schedule) implements ResourceEvent {}
    @TypeName("resource-type-set")
    record ResourceTypeSet(String resourceId, String resourceType) implements ResourceEvent {}
    @TypeName("external-ref-set")
    record ExternalRefSet(String resourceId, String externalRef, String externalGroupRef) implements ResourceEvent {}
}
