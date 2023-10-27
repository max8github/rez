package com.rezhub.reservation.customer.facility;

import com.rezhub.reservation.customer.dto.Address;
import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.resource.dto.Resource;
import com.rezhub.reservation.resource.ResourceEntity;
import kalix.javasdk.StatusCode;
import kalix.javasdk.annotations.Acl;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import static com.rezhub.reservation.dto.Reservation.FACILITY;

@Id("facilityId")
@TypeId("facility")
@RequestMapping("/facility/{facilityId}")
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

  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
  @PostMapping("/create")
  public Effect<String> create(@RequestBody Facility facility) {
    String id = commandContext().entityId();
    String name = facility.name();
    String stateName = currentState().name();
    log.info("creating facility {}, {}", name, id);
    if(name == null || name.isEmpty()) {
      return effects().error("A Facility must have a name", StatusCode.ErrorCode.BAD_REQUEST);
    } else if(name.equals(Resource.FORBIDDEN_NAME)) {
      return effects().error("Invalid name: name '" + name + "' cannot be used.", StatusCode.ErrorCode.BAD_REQUEST);
    } else if(!stateName.equals(Resource.FORBIDDEN_NAME) && !name.equals(stateName)) {
        return effects().error("Entity with id " + commandContext().entityId() + " is already created", StatusCode.ErrorCode.BAD_REQUEST);
    }
    if(!id.startsWith(FACILITY) && !id.startsWith("stub")) {
      String message = "The id provided, '" + id + "', is not valid for a Pool: it must start with the prefix '" + FACILITY + "' (or 'stub' for tests)";
      log.error(message);
      return effects().error(message, StatusCode.ErrorCode.BAD_REQUEST);
    } else {
      return effects()
        .emitEvent(new FacilityEvent.Created(id, facility))
        .thenReply(newState -> id);
    }
  }

  @SuppressWarnings("unused")
  @EventHandler
  public FacilityState created(FacilityEvent.Created created) {
    var dto = created.facility();
    return FacilityState.create(created.entityId())
      .withName(dto.name())
      .withAddressState(new AddressState(dto.address().street(), dto.address().city()))
      .withResourceIds(dto.resourceIds());
  }

  @PutMapping("/rename/{newName}")
  public Effect<String> rename(@PathVariable String newName) {
    return effects()
      .emitEvent(new FacilityEvent.Renamed(newName))
      .thenReply(newState -> "OK");
  }

  @SuppressWarnings("unused")
  @EventHandler
  public FacilityState renamed(FacilityEvent.Renamed renamed) {
    return currentState().withName(renamed.newName());
  }

  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
  @PutMapping("/changeAddress")
  public Effect<String> changeAddress(@RequestBody AddressState addressState) {
    return effects()
      .emitEvent(new FacilityEvent.AddressChanged(addressState))
      .thenReply(newState -> "OK");
  }

  @SuppressWarnings("unused")
  @EventHandler
  public FacilityState addressChanged(FacilityEvent.AddressChanged addressChanged) {
    AddressState addressState = addressChanged.addressState();
    return currentState().withAddressState(new AddressState(addressState.street(), addressState.city()));
  }

  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
  @PostMapping("/resource/{resourceId}")
  public Effect<String> requestResourceCreateAndRegister(@RequestBody FacilityResourceRequest resource, @PathVariable String resourceId) {
    return effects()
      .emitEvent(new FacilityEvent.ResourceCreateAndRegisterRequested(currentState().facilityId(), resource.resourceName, resourceId))
      .thenReply(newState -> resourceId);
  }

  @SuppressWarnings("unused")
  @EventHandler
  public FacilityState resourceCreateAndRegisterRequested(FacilityEvent.ResourceCreateAndRegisterRequested event) {
    return currentState();
  }

  @PutMapping("/resource/{resourceId}")
  public Effect<String> registerResource(@PathVariable String resourceId) {
    log.info("registering resource with id {}", resourceId);
    return effects()
      .emitEvent(new FacilityEvent.ResourceRegistered(resourceId))
      .thenReply(newState -> resourceId);
  }

  @SuppressWarnings("unused")
  @EventHandler
  public FacilityState resourceRegistered(FacilityEvent.ResourceRegistered event) {
    return currentState().registerResource(event.resourceId());
  }

  @DeleteMapping("/resource/{resourceId}")
  public Effect<String> unregisterResource(@PathVariable String resourceId) {
    if (!currentState().resourceIds().contains(resourceId)) {
      return effects().error("Cannot remove resource " + resourceId + " because it is not in the facility.");
    }
    return effects()
      .emitEvent(new FacilityEvent.ResourceUnregistered(resourceId))
      .thenReply(newState -> "OK");
  }

  @SuppressWarnings("unused")
  @EventHandler
  public FacilityState resourceUnregistered(FacilityEvent.ResourceUnregistered event) {
    return currentState().unregisterResource(event.resourceId());
  }

  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
  @GetMapping()
  public Effect<Facility> getFacility() {
    FacilityState state = currentState();
    Address address = new Address(state.addressState().street(), state.addressState().city());
    return effects().reply(new Facility(state.name(), address, state.resourceIds()));
  }

  @PostMapping("/checkAvailability")
  public Effect<String> checkAvailability(@RequestBody ResourceEntity.CheckAvailability command) {
    log.info("FacilityEntity {} delegates availability check for reservation request {}", entityId, command.reservationId());
    return effects()
      .emitEvent(new FacilityEvent.AvalabilityRequested(command.reservationId(), command.reservation(), currentState().resourceIds()))
      .thenReply(newState -> "OK");
  }

  @SuppressWarnings("unused")
  @EventHandler
  public FacilityState availabilityChecked(FacilityEvent.AvalabilityRequested event) {
    return currentState();
  }

  public record FacilityResourceRequest(String resourceName) {}
}