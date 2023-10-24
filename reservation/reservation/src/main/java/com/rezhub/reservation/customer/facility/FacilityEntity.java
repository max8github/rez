package com.rezhub.reservation.customer.facility;

import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.customer.asset.dto.Asset;
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
    return FacilityState.create(entityId).withName("noname").withAddress(new com.rezhub.reservation.customer.facility.Address("nostreet", "nocity"));
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
      .withAddress(new com.rezhub.reservation.customer.facility.Address(dto.address().street(), dto.address().city()))
      .withAssetIds(dto.assetIds());
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
  public Effect<String> changeAddress(@RequestBody com.rezhub.reservation.customer.facility.Address address) {
    return effects()
      .emitEvent(new FacilityEvent.AddressChanged(address))
      .thenReply(newState -> "OK");
  }

  @SuppressWarnings("unused")
  @EventHandler
  public FacilityState addressChanged(FacilityEvent.AddressChanged addressChanged) {
    com.rezhub.reservation.customer.facility.Address address = addressChanged.address();
    return currentState().withAddress(new com.rezhub.reservation.customer.facility.Address(address.street(), address.city()));
  }

  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
  @PostMapping("/asset/submit")
  public Effect<String> submitAsset(@RequestBody Asset asset) {
    String id = asset.assetId();
    return effects()
      .emitEvent(new FacilityEvent.AssetSubmitted(currentState().facilityId(), asset, id))
      .thenReply(newState -> id);
  }

  @SuppressWarnings("unused")
  @EventHandler
  public FacilityState assetIdSubmitted(FacilityEvent.AssetSubmitted event) {
    return currentState();
  }

  @PostMapping("/asset/{assetId}")
  public Effect<String> addAssetId(@PathVariable String assetId) {
    log.info("added asset id {}", assetId);
    return effects()
      .emitEvent(new FacilityEvent.AssetIdAdded(assetId))
      .thenReply(newState -> assetId);
  }

  @SuppressWarnings("unused")
  @EventHandler
  public FacilityState assetIdAdded(FacilityEvent.AssetIdAdded event) {
    return currentState().addAsset(event.assetId());
  }

  @DeleteMapping("/asset/{assetId}")
  public Effect<String> removeAssetId(@PathVariable String assetId) {
    if (!currentState().assetIds().contains(assetId)) {
      return effects().error("Cannot remove asset " + assetId + " because it is not in the facility.");
    }
    return effects()
      .emitEvent(new FacilityEvent.AssetIdRemoved(assetId))
      .thenReply(newState -> "OK");
  }

  @SuppressWarnings("unused")
  @EventHandler
  public FacilityState assetIdRemoved(FacilityEvent.AssetIdRemoved event) {
    return currentState().removeAsset(event.assetId());
  }

  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
  @GetMapping()
  public Effect<Facility> getFacility() {
    FacilityState facilityState = currentState();
    com.rezhub.reservation.customer.facility.Address addressState = facilityState.address();
    com.rezhub.reservation.customer.facility.dto.Address address = new com.rezhub.reservation.customer.facility.dto.Address(addressState.street(), addressState.city());
    return effects().reply(new Facility(facilityState.name(), address, facilityState.assetIds()));
  }
}