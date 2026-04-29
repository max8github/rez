package com.rezhub.reservation.resource;

import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.resource.dto.Resource;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

import static com.rezhub.reservation.resource.dto.Resource.FORBIDDEN_NAME;

@Component(id = "resource")
public class ResourceEntity extends EventSourcedEntity<ResourceState, ResourceEvent> {
    private static final Logger log = LoggerFactory.getLogger(ResourceEntity.class);
    private final String entityId;

    public ResourceEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    @Override
    public ResourceState emptyState() {
        return ResourceState.initialize(FORBIDDEN_NAME, null);
    }

    @Override
    public ResourceState applyEvent(ResourceEvent event) {
        return switch (event) {
            case ResourceEvent.FacilityResourceCreated e -> ResourceState.initialize(e.name(), e.calendarId());
            case ResourceEvent.ResourceCreated e -> ResourceState.initialize(e.resourceName(), e.calendarId());
            case ResourceEvent.AvalabilityChecked e -> currentState();
            case ResourceEvent.ReservationAccepted e -> currentState().set(ResourceState.roundToValidTime(e.reservation().dateTime()), e.reservationId());
            case ResourceEvent.ReservationRejected e -> currentState();
            case ResourceEvent.ReservationCanceled e -> currentState().cancel(e.dateTime(), e.reservationId());
            case ResourceEvent.WeeklyScheduleUpdated e -> currentState().withWeeklySchedule(e.schedule());
            case ResourceEvent.ResourceTypeSet e -> currentState().withResourceType(e.resourceType());
            case ResourceEvent.ExternalRefSet e -> currentState().withExternalRef(e.externalRef(), e.externalGroupRef());
            case ResourceEvent.ResourceDeleted e -> currentState();
        };
    }

    public Effect<String> createFacilityResource(CreateChildResource resCommand) {
        String id = commandContext().entityId();
        String assetName = resCommand.resource().resourceName();
        return effects()
            .persist(new ResourceEvent.FacilityResourceCreated(id, assetName, resCommand.facilityId(), resCommand.resource().calendarId()))
            .thenReply(newState -> id);
    }

    public Effect<String> create(Resource command) {
        String id = commandContext().entityId();
        String name = command.resourceName();
        String stateName = currentState().name();

        if (name == null || name.isEmpty()) {
            return effects().error("A Resource must have a name");
        } else if (name.equals(Resource.FORBIDDEN_NAME)) {
            return effects().error("Invalid name: name '" + name + "' cannot be used.");
        } else if (!stateName.equals(Resource.FORBIDDEN_NAME) && !name.equals(stateName)) {
            return effects().error("Entity with id " + commandContext().entityId() + " is already created");
        }

        return effects()
            .persist(new ResourceEvent.ResourceCreated(id, command.resourceName(), command.calendarId()))
            .thenReply(newState -> id);
    }

    public Effect<String> checkAvailability(CheckAvailability command) {
        LocalDateTime validTime = ResourceState.roundToValidTime(command.reservation().dateTime());
        boolean vacant = currentState().isReservableAt(validTime);
        String yes = vacant ? "" : "NOT ";
        log.info("Resource {} ({}) can {}accept reservation {} ", currentState().name(), entityId, yes, command.reservationId);
        return effects()
            .persist(new ResourceEvent.AvalabilityChecked(entityId, command.reservationId(), vacant))
            .thenReply(newState -> "OK");
    }

    public Effect<String> reserve(Reserve command) {
        LocalDateTime validTime = ResourceState.roundToValidTime(command.reservation().dateTime());
        if (currentState().isReservableAt(validTime)) {
            log.info("Resource {} {} accepts reservation {} ", currentState().name(), entityId, command.reservationId);
            return effects()
                .persist(new ResourceEvent.ReservationAccepted(entityId, command.reservationId(),
                    command.reservation()))
                .thenReply(newState -> "OK");
        } else {
            log.info("Resource {} {} rejects reservation {}", currentState().name(), entityId, command.reservationId);
            return effects()
                .persist(new ResourceEvent.ReservationRejected(entityId, command.reservationId(),
                    command.reservation()
                ))
                .thenReply(newState -> "UNAVAILABLE resource");
        }
    }

    public Effect<String> cancel(CancelReservation command) {
        LocalDateTime validTime = ResourceState.roundToValidTime(command.dateTime());
        log.info("Cancelling reservation {} from resource {} on dateTime {} ", command.reservationId(), entityId, validTime);
        return effects()
            .persist(new ResourceEvent.ReservationCanceled(entityId, command.reservationId(), validTime))
            .thenReply(newState -> "OK");
    }

    public Effect<String> deleteResource() {
        log.info("Deleting resource {}", entityId);
        return effects()
            .persist(new ResourceEvent.ResourceDeleted(entityId))
            .deleteEntity()
            .thenReply(newState -> "OK");
    }

    public ReadOnlyEffect<ResourceState> getResource() {
        return effects().reply(currentState());
    }

    /** Command record — Akka SDK cannot deserialize raw generic Map types as command parameters. */
    public record WeeklyScheduleCommand(Map<String, java.util.List<String>> hours) {}

    public Effect<String> setWeeklySchedule(WeeklyScheduleCommand cmd) {
        log.info("Resource {} updating weekly schedule", entityId);
        Map<DayOfWeek, Set<LocalTime>> schedule = cmd.hours().entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                e -> DayOfWeek.valueOf(e.getKey()),
                e -> e.getValue().stream().map(LocalTime::parse).collect(java.util.stream.Collectors.toSet())
            ));
        return effects()
            .persist(new ResourceEvent.WeeklyScheduleUpdated(entityId, schedule))
            .thenReply(newState -> "OK");
    }

    public Effect<String> setResourceType(String resourceType) {
        log.info("Resource {} setting resourceType to {}", entityId, resourceType);
        return effects()
            .persist(new ResourceEvent.ResourceTypeSet(entityId, resourceType))
            .thenReply(newState -> "OK");
    }

    public Effect<String> setExternalRef(SetExternalRef command) {
        log.info("Resource {} setting externalRef={} externalGroupRef={}", entityId, command.externalRef(), command.externalGroupRef());
        return effects()
            .persist(new ResourceEvent.ExternalRefSet(entityId, command.externalRef(), command.externalGroupRef()))
            .thenReply(newState -> "OK");
    }

    public record CreateResourceCommand(String poolId, Resource resourceDto) {}
    public record CreateChildResource(String facilityId, Resource resource) {}

    public record CheckAvailability(String reservationId, Reservation reservation) {}

    public record Reserve(String reservationId, Reservation reservation) {}

    public record CancelReservation(String reservationId, LocalDateTime dateTime) {}

    public record SetExternalRef(String externalRef, String externalGroupRef) {}
}
