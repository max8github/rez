package com.rezhub.reservation.customer.facility;

import com.rezhub.reservation.customer.dto.Address;
import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.resource.dto.Resource;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages facility provisioning (create, rename, botToken).
 * Resource grouping is no longer owned here — each ResourceEntity declares its
 * facility via externalGroupRef; ResourcesByFacilityView provides the reverse lookup.
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

    public ReadOnlyEffect<Facility> getFacility() {
        FacilityState state = currentState();
        Address address = new Address(state.addressState().street(), state.addressState().city());
        return effects().reply(new Facility(state.name(), address, state.timezone(), state.botToken(), state.adminUserIds()));
    }
}
