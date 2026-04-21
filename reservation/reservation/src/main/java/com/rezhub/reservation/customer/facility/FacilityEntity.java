package com.rezhub.reservation.customer.facility;

import com.rezhub.reservation.customer.dto.Address;
import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.resource.dto.Resource;
import com.rezhub.reservation.resource.ResourceEntity;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Compatibility shim for the Telegram / BookingService booking path.
 *
 * <p>Provisioning operations (create, rename, registerResource, etc.) remain active.
 * The booking-dispatch path ({@link #checkAvailability}) is deprecated: Rez no longer
 * treats Facility as a first-class booking entity. New callers use
 * {@code BookingEndpoint} with a flat set of resourceIds resolved externally.
 * {@code checkAvailability} and the {@code FacilityEvent.AvalabilityRequested} event
 * will be removed once BookingService is migrated.
 */
@Component(id = "facility")
public class FacilityEntity extends EventSourcedEntity<FacilityState, FacilityEvent> {
    private static final Logger log = LoggerFactory.getLogger(FacilityEntity.class);

    private final String entityId;

    public FacilityEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    @Override
    public FacilityState emptyState() {
        return FacilityState.create(entityId).withName(Resource.FORBIDDEN_NAME).withAddressState(new AddressState("nostreet", "nocity"));
    }

    @Override
    public FacilityState applyEvent(FacilityEvent event) {
        return switch (event) {
            case FacilityEvent.Created e -> {
                var dto = e.facility();
                yield FacilityState.create(e.entityId())
                    .withName(dto.name())
                    .withAddressState(new AddressState(dto.address().street(), dto.address().city()))
                    .withResourceIds(FacilityState.normalizeResourceIds(dto.resourceIds()))
                    .withTimezone(dto.timezone())
                    .withBotToken(dto.botToken())
                    .withAdminUserIds(dto.adminUserIds());
            }
            case FacilityEvent.Renamed e -> currentState().withName(e.newName());
            case FacilityEvent.AddressChanged e -> {
                AddressState addressState = e.addressState();
                yield currentState().withAddressState(new AddressState(addressState.street(), addressState.city()));
            }
            case FacilityEvent.BotTokenUpdated e -> currentState().withBotToken(e.botToken());
            case FacilityEvent.ResourceCreateAndRegisterRequested e -> currentState();
            case FacilityEvent.ResourceRegistered e -> currentState().registerResource(e.resourceId());
            case FacilityEvent.ResourceUnregistered e -> currentState().unregisterResource(e.resourceId());
            case FacilityEvent.AvalabilityRequested e -> currentState();
        };
    }

    public Effect<String> create(Facility facility) {
        String id = commandContext().entityId();
        String name = facility.name();
        String stateName = currentState().name();
        log.info("creating facility {}, {}", name, id);
        if (name == null || name.isEmpty()) {
            return effects().error("A Facility must have a name");
        } else if (name.equals(Resource.FORBIDDEN_NAME)) {
            return effects().error("Invalid name: name '" + name + "' cannot be used.");
        } else if (!stateName.equals(Resource.FORBIDDEN_NAME) && !name.equals(stateName)) {
            return effects().error("Entity with id " + commandContext().entityId() + " is already created");
        }
        return effects()
            .persist(new FacilityEvent.Created(id, facility))
            .thenReply(newState -> id);
    }

    public Effect<String> rename(String newName) {
        return effects()
            .persist(new FacilityEvent.Renamed(newName))
            .thenReply(newState -> "OK");
    }

    public Effect<String> changeAddress(AddressState addressState) {
        return effects()
            .persist(new FacilityEvent.AddressChanged(addressState))
            .thenReply(newState -> "OK");
    }

    public Effect<String> clearBotToken() {
        return effects()
            .persist(new FacilityEvent.BotTokenUpdated(currentState().facilityId(), null, currentState().timezone()))
            .thenReply(newState -> "OK");
    }

    public Effect<String> requestResourceCreateAndRegister(CreateAndRegisterResource command) {
        return effects()
            .persist(new FacilityEvent.ResourceCreateAndRegisterRequested(currentState().facilityId(), command.resourceName(), command.resourceId(), command.calendarId()))
            .thenReply(newState -> command.resourceId());
    }

    public Effect<String> registerResource(String resourceId) {
        log.info("registering resource with id {}", resourceId);
        return effects()
            .persist(new FacilityEvent.ResourceRegistered(resourceId))
            .thenReply(newState -> resourceId);
    }

    public Effect<String> unregisterResource(String resourceId) {
        if (!currentState().resourceIds().contains(resourceId)) {
            return effects().error("Cannot remove resource " + resourceId + " because it is not in the facility.");
        }
        return effects()
            .persist(new FacilityEvent.ResourceUnregistered(resourceId))
            .thenReply(newState -> "OK");
    }

    public ReadOnlyEffect<Facility> getFacility() {
        FacilityState state = currentState();
        Address address = new Address(state.addressState().street(), state.addressState().city());
        return effects().reply(new Facility(state.name(), address, FacilityState.normalizeResourceIds(state.resourceIds()),
                state.timezone(), state.botToken(), state.adminUserIds()));
    }

    /**
     * @deprecated Booking no longer flows through Facility. Use {@code BookingEndpoint}
     * with a flat set of resourceIds. Will be removed with {@code FacilityEvent.AvalabilityRequested}.
     */
    @Deprecated
    public Effect<String> checkAvailability(ResourceEntity.CheckAvailability command) {
        log.info("FacilityEntity {} delegates availability check for reservation request {}", entityId, command.reservationId());
        return effects()
            .persist(new FacilityEvent.AvalabilityRequested(
                command.reservationId(),
                command.reservation(),
                FacilityState.normalizeResourceIds(currentState().resourceIds())
            ))
            .thenReply(newState -> "OK");
    }

    public record FacilityResourceRequest(String resourceName) {}

    public record CreateAndRegisterResource(String resourceName, String resourceId, String calendarId) {}
}
