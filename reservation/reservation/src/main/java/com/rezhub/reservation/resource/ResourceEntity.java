package com.rezhub.reservation.resource;

import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.resource.dto.Resource;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

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
        return ResourceState.initialize(FORBIDDEN_NAME);
    }

    @Override
    public ResourceState applyEvent(ResourceEvent event) {
        return switch (event) {
            case ResourceEvent.FacilityResourceCreated e -> ResourceState.initialize(e.name());
            case ResourceEvent.ResourceCreated e -> ResourceState.initialize(e.resourceName());
            case ResourceEvent.AvalabilityChecked e -> currentState();
            case ResourceEvent.ReservationAccepted e -> currentState().set(ResourceState.roundToValidTime(e.reservation().dateTime()), e.reservationId());
            case ResourceEvent.ReservationRejected e -> currentState();
            case ResourceEvent.ReservationCanceled e -> currentState().cancel(e.dateTime(), e.reservationId());
        };
    }

    public Effect<String> createFacilityResource(CreateChildResource resCommand) {
        String id = commandContext().entityId();
        String assetName = resCommand.resource().resourceName();
        return effects()
            .persist(new ResourceEvent.FacilityResourceCreated(id, assetName, resCommand.facilityId()))
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
            .persist(new ResourceEvent.ResourceCreated(id, command.resourceName()))
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

    public ReadOnlyEffect<ResourceState> getResource() {
        return effects().reply(currentState());
    }

    public record CreateResourceCommand(String poolId, Resource resourceDto) {}
    public record CreateChildResource(String facilityId, Resource resource) {}

    public record CheckAvailability(String reservationId, Reservation reservation) {}

    public record Reserve(String reservationId, Reservation reservation) {}

    public record CancelReservation(String reservationId, LocalDateTime dateTime) {}
}
