package com.mcalder.recordhub.customer.facility;

import com.mcalder.recordhub.customer.facility.dto.Address;
import com.mcalder.recordhub.customer.facility.dto.Facility;
import com.mcalder.recordhub.customer.resource.dto.Resource;
import kalix.javasdk.annotations.Acl;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

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
    return FacilityState.create(entityId).withName("noname").withAddress(new com.mcalder.recordhub.customer.facility.Address("nostreet", "nocity"));
  }

  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
  @PostMapping("/create")
  public Effect<String> create(@RequestBody Facility facility) {
    log.info("created facility {}", facility.name());
    return effects()
      .emitEvent(new FacilityEvent.Created(entityId, facility))
      .thenReply(newState -> entityId);
  }

  @SuppressWarnings("unused")
  @EventHandler
  public FacilityState created(FacilityEvent.Created created) {
    var dto = created.facility();
    return FacilityState.create(created.entityId())
      .withName(dto.name())
      .withAddress(new com.mcalder.recordhub.customer.facility.Address(dto.address().street(), dto.address().city()))
      .withResourceIds(dto.resourceIds());
  }

  @PostMapping("/rename/{newName}")
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
  @PostMapping("/changeAddress")
  public Effect<String> changeAddress(@RequestBody Address address) {
    return effects()
      .emitEvent(new FacilityEvent.AddressChanged(address))
      .thenReply(newState -> "OK");
  }

  @SuppressWarnings("unused")
  @EventHandler
  public FacilityState addressChanged(FacilityEvent.AddressChanged addressChanged) {
    Address address = addressChanged.address();
    return currentState().withAddress(new com.mcalder.recordhub.customer.facility.Address(address.street(), address.city()));
  }

  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
  @PostMapping("/resource/submit")
  public Effect<String> submitResource(@RequestBody Resource resourceDto) {
    String id = resourceDto.resourceId();
    return effects()
      .emitEvent(new FacilityEvent.ResourceSubmitted(currentState().facilityId(), resourceDto, id))
      .thenReply(newState -> id);
  }

  @SuppressWarnings("unused")
  @EventHandler
  public FacilityState resourceIdSubmitted(FacilityEvent.ResourceSubmitted event) {
    return currentState();
  }

  @PostMapping("/resource/{resourceId}")
  public Effect<String> addResourceId(@PathVariable String resourceId) {
    log.info("added resource id {}", resourceId);
    return effects()
      .emitEvent(new FacilityEvent.ResourceIdAdded(resourceId))
      .thenReply(newState -> resourceId);
  }

  @SuppressWarnings("unused")
  @EventHandler
  public FacilityState resourceIdAdded(FacilityEvent.ResourceIdAdded event) {
    return currentState().withResourceId(event.resourceId());
  }

  @DeleteMapping("/resource/{resourceId}")
  public Effect<String> removeResourceId(@PathVariable String resourceId) {
    if (!currentState().resourceIds().contains(resourceId)) {
      return effects().error("Cannot remove resource " + resourceId + " because it is not in the facility.");
    }
    return effects()
      .emitEvent(new FacilityEvent.ResourceIdRemoved(resourceId))
      .thenReply(newState -> "OK");
  }

  @SuppressWarnings("unused")
  @EventHandler
  public FacilityState resourceIdRemoved(FacilityEvent.ResourceIdRemoved event) {
    return currentState().withoutResourceId(event.resourceId());
  }

  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
  @GetMapping()
  public Effect<Facility> getFacility() {
    FacilityState facilityState = currentState();
    com.mcalder.recordhub.customer.facility.Address addressState = facilityState.address();
    Address address = new Address(addressState.street(), addressState.city());
    return effects().reply(new Facility(facilityState.name(), address, facilityState.resourceIds()));
  }
}